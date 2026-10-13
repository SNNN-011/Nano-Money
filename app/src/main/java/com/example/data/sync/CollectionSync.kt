package com.example.data.sync

import com.example.data.SyncEntity
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.tasks.await

/**
 * Engine sinkronisasi untuk satu koleksi Firestore.
 *
 * Kontrak:
 * - push() hanya mengirim baris berstatus PENDING, maksimal [BATCH_LIMIT] per batch,
 *   dan menandai SYNCED hanya setelah commit benar-benar berhasil.
 * - pull() hanya mengambil dokumen dengan updatedAt > cursor, lalu menerapkannya
 *   memakai [ConflictResolver]. updatedAt dari remote tidak pernah ditimpa.
 * - Kegagalan dilempar sebagai exception; baris lokal tetap PENDING agar dicoba lagi.
 */
class CollectionSync<T : SyncEntity>(
    val name: String,
    private val collectionRef: CollectionReference,
    private val pendingItems: suspend () -> List<T>,
    private val findLocal: suspend (String) -> T?,
    private val applyRemote: suspend (T) -> Unit,
    private val markSynced: suspend (List<String>) -> Unit,
    private val toDocument: (T) -> Map<String, Any?>,
    private val fromDocument: (DocumentSnapshot) -> T?
) {

    data class PushResult(val pushed: Int)
    data class PullResult(val applied: Int, val newestUpdatedAt: Long)

    suspend fun push(): PushResult {
        val items = pendingItems()
        if (items.isEmpty()) return PushResult(0)

        var pushed = 0
        items.chunked(BATCH_LIMIT).forEach { chunk ->
            val batch: WriteBatch = FirebaseFirestore.getInstance().batch()
            chunk.forEach { item ->
                batch.set(collectionRef.document(item.syncKey), toDocument(item))
            }
            batch.commit().await()

            markSynced(chunk.map { it.syncKey })
            pushed += chunk.size
        }
        return PushResult(pushed)
    }

    suspend fun pull(lastSync: Long): PullResult {
        val snapshot = collectionRef
            .whereGreaterThan(FIELD_UPDATED_AT, lastSync)
            .orderBy(FIELD_UPDATED_AT)
            .get()
            .await()

        var applied = 0
        var newest = lastSync
        for (doc in snapshot.documents) {
            val remote = fromDocument(doc) ?: continue
            if (remote.updatedAt > newest) newest = remote.updatedAt

            val local = findLocal(remote.syncKey)
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
            val remote = fromDocument(doc) ?: continue
            val local = findLocal(remote.syncKey)
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
    }
}