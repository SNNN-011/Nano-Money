package com.example.data.sync

import com.example.data.SyncEntity
import com.example.data.SyncStatus
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import java.util.Date
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * Engine sinkronisasi untuk satu koleksi Firestore.
 *
 * Kontrak:
 * - push() hanya mengirim baris berstatus PENDING (yang tombstone-nya tidak aktif),
 *   maksimal [BATCH_LIMIT] per batch, dan menandai SYNCED secara bersyarat: hanya
 *   baris yang updatedAt-nya belum berubah sejak dibaca (lihat H1).
 * - pull() hanya mengambil dokumen dengan updatedAt > cursor, lalu menerapkannya
 *   memakai [ConflictResolver]. updatedAt dari remote tidak pernah ditimpa.
 * - Baris lokal PENDING tidak pernah ditimpa remote: edit lokal yang belum terkirim
 *   harus menang, sekaligus mencegah loop dengan dokumen echo serverTimestamp.
 * - Kegagalan dilempar sebagai exception; baris lokal tetap PENDING agar dicoba lagi.
 */
class CollectionSync<T : SyncEntity>(
    val name: String,
    private val collectionRef: CollectionReference,
    private val pendingItems: suspend () -> List<T>,
    private val findLocal: suspend (String) -> T?,
    private val applyRemote: suspend (T) -> Unit,
    private val markSynced: suspend (List<T>) -> Unit,
    private val toDocument: (T) -> Map<String, Any?>,
    private val fromDocument: (DocumentSnapshot) -> T?,
    private val isTombstoned: (String) -> Boolean = { false }
) {

    data class PushResult(val pushed: Int)
    data class PullResult(val applied: Int, val newestUpdatedAt: Long)

    suspend fun push(): PushResult {
        val items = pendingItems().filterNot { isTombstoned(it.syncKey) }
        if (items.isEmpty()) return PushResult(0)

        var pushed = 0
        items.chunked(BATCH_LIMIT).forEach { chunk ->
            val batch: WriteBatch = FirebaseFirestore.getInstance().batch()
            chunk.forEach { item ->
                batch.set(collectionRef.document(item.syncKey), toDocument(item))
            }
            withTimeout(OP_TIMEOUT_MS) { batch.commit().await() }

            markSynced(chunk)
            pushed += chunk.size
        }
        return PushResult(pushed)
    }

    suspend fun pull(lastSync: Long): PullResult {
        // updatedAt ditulis sebagai serverTimestamp (Timestamp), jadi batas query juga
        // harus Timestamp; Long lama tetap terbaca lewat fromDocument masing-masing.
        val snapshot = withTimeout(OP_TIMEOUT_MS) {
            collectionRef
                .whereGreaterThan(FIELD_UPDATED_AT, Timestamp(Date(lastSync)))
                .orderBy(FIELD_UPDATED_AT)
                .get()
                .await()
        }

        var applied = 0
        var newest = lastSync
        for (doc in snapshot.documents) {
            // Echo tulisan lokal belum punya updatedAt server; jangan diproses.
            if (doc.metadata.hasPendingWrites()) continue
            if (isTombstoned(doc.id)) continue
            val remote = fromDocument(doc) ?: continue
            // Kursor memakai updatedAt yang ditulis serverTimestamp sehingga jam
            // perangkat yang meleset tidak membuat dokumen lain terlewat (H12).
            if (remote.updatedAt > newest) newest = remote.updatedAt

            val local = findLocal(remote.syncKey)
            if (local?.syncStatus == SyncStatus.PENDING) continue
            if (ConflictResolver.shouldApplyRemote(local?.updatedAt, remote.updatedAt)) {
                applyRemote(remote)
                applied++
            }
        }
        return PullResult(applied, newest)
    }

    /**
     * Menerapkan dokumen dari snapshot listener memakai aturan konflik yang sama dengan
     * pull(), sehingga jalur realtime dan manual tidak bisa berbeda aturan.
     */
    suspend fun applyRemoteDocuments(documents: List<DocumentSnapshot>): Int {
        var applied = 0
        for (doc in documents) {
            if (doc.metadata.hasPendingWrites()) continue
            if (isTombstoned(doc.id)) continue
            val remote = fromDocument(doc) ?: continue
            val local = findLocal(remote.syncKey)
            if (local?.syncStatus == SyncStatus.PENDING) continue
            if (ConflictResolver.shouldApplyRemote(local?.updatedAt, remote.updatedAt)) {
                applyRemote(remote)
                applied++
            }
        }
        return applied
    }

    fun collection(): CollectionReference = collectionRef

    companion object {
        const val BATCH_LIMIT = 500
        const val FIELD_UPDATED_AT = "updatedAt"
        const val FIELD_IS_DELETED = "isDeleted"
        const val FIELD_SYNC_KEY = "syncKey"

        /** Batas tunggu satu operasi jaringan, supaya sync tidak menggantung selamanya. */
        const val OP_TIMEOUT_MS = 30_000L
    }
}
