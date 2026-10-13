package com.example.data.sync

import android.util.Log
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Adopsi satu kali untuk dokumen cloud yang masih memakai skema lama.
 *
 * Dokumen lama (ditulis sinkron versi sebelumnya) tidak punya field `updatedAt` maupun
 * `syncKey`, jadi tidak akan pernah terbawa query `updatedAt > cursor`. Tanpa adopsi
 * ini, data lama di cloud tidak akan pernah muncul di aplikasi.
 *
 * Alur: dokumen legacy -> entitas lokal (PENDING, updatedAt = sekarang) -> ditulis
 * ulang ke dokumen baru berbasis syncKey -> dokumen lama dihapus. Dijalankan sekali
 * per koleksi lewat flag di SyncCursors.
 */
object LegacyAdoption {

    private const val TAG = "SyncManager"

    /** Dokumen dianggap legacy bila tidak punya updatedAt maupun syncKey. */
    fun isLegacyDocument(data: Map<String, Any?>): Boolean =
        !data.containsKey(CollectionSync.FIELD_UPDATED_AT) &&
            !data.containsKey(CollectionSync.FIELD_SYNC_KEY)

    /** Prefix kunci untuk data yang diadopsi, mis. "legacy:12". */
    fun legacySyncKey(docId: String): String = "legacy:$docId"

    /**
     * Membaca semua dokumen legacy pada koleksi, mengembalikanYA ke pemanggil lewat
     * [onAdopt] dan mengembalikan daftar reference yang harus dihapus setelah push sukses.
     */
    suspend fun <T> adopt(
        collectionRef: CollectionReference,
        fromLegacyDocument: (com.google.firebase.firestore.DocumentSnapshot) -> T?,
        onAdopt: suspend (T) -> Unit
    ): List<com.google.firebase.firestore.DocumentReference> {
        val snapshot = collectionRef.get().await()
        val legacyDocs = snapshot.documents.filter { isLegacyDocument(it.data ?: emptyMap()) }
        if (legacyDocs.isEmpty()) return emptyList()

        var adopted = 0
        for (doc in legacyDocs) {
            val entity = fromLegacyDocument(doc) ?: continue
            runCatching { onAdopt(entity) }
                .onSuccess { adopted++ }
                .onFailure { Log.w(TAG, "Adopsi gagal untuk ${doc.id}: ${it.message}") }
        }
        Log.i(TAG, "Adopsi ${adopted}/${legacyDocs.size} dokumen legacy dari ${collectionRef.id}")
        return legacyDocs.map { it.reference }
    }

    /** Menghapus dokumen legacy setelah versi barunya sudah aman di cloud. */
    suspend fun deleteDocs(refs: List<com.google.firebase.firestore.DocumentReference>) {
        if (refs.isEmpty()) return
        refs.chunked(400).forEach { chunk ->
            val batch = FirebaseFirestore.getInstance().batch()
            chunk.forEach { batch.delete(it) }
            batch.commit().await()
        }
    }
}