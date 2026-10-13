package com.example.data.sync

import android.util.Log
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * Adopsi satu kali untuk dokumen cloud yang masih memakai skema lama.
 *
 * Dokumen lama (ditulis sinkron versi sebelumnya) tidak punya field `updatedAt` maupun
 * `syncKey`, jadi tidak akan pernah terbawa query `updatedAt > cursor`. Tanpa adopsi
 * ini, data lama di cloud tidak akan pernah muncul di aplikasi.
 *
 * Alur: dokumen legacy -> entitas lokal (PENDING) -> ditulis ulang ke dokumen baru
 * berbasis syncKey -> **diverifikasi ada di cloud** -> dokumen lama dihapus.
 *
 * Urutan itu tidak boleh dibalik. Dokumen legacy adalah satu-satunya salinan data
 * tersebut di cloud, jadi ia baru boleh dihapus setelah versi penggantinya benar-benar
 * tersimpan. Karena itu [adopt] hanya mengembalikan dokumen yang sukses diadopsi, dan
 * [deleteVerified] menolak menghapus bila pasangannya belum ada di cloud.
 */
object LegacyAdoption {

    private const val TAG = "SyncManager"

    /** Dokumen legacy yang sudah jadi entitas lokal, beserta pengganti barunya di cloud. */
    data class Adopted(
        val legacyRef: DocumentReference,
        val newDocId: String
    )

    /** Dokumen dianggap legacy bila tidak punya updatedAt maupun syncKey. */
    fun isLegacyDocument(data: Map<String, Any?>): Boolean =
        !data.containsKey(CollectionSync.FIELD_UPDATED_AT) &&
            !data.containsKey(CollectionSync.FIELD_SYNC_KEY)

    /** Prefix kunci untuk data yang diadopsi, mis. "legacy:12". */
    fun legacySyncKey(docId: String): String = "legacy:$docId"

    /**
     * Membaca dokumen legacy lalu memetakannya menjadi entitas lokal.
     *
     * Hanya dokumen yang **berhasil** dipetakan dan ditulis ke lokal yang dikembalikan;
     * dokumen yang gagal mapping atau gagal ditulis sengaja tidak ikut, supaya tidak
     * ikut terhapus nanti dan tetap bisa dicoba ulang pada sync berikutnya.
     */
    suspend fun <T> adopt(
        collectionRef: CollectionReference,
        fromLegacyDocument: (DocumentSnapshot) -> T?,
        onAdopt: suspend (T) -> Unit,
        newDocIdOf: (T) -> String
    ): List<Adopted> {
        val snapshot = withTimeout(CollectionSync.OP_TIMEOUT_MS) { collectionRef.get().await() }
        val legacyDocs = snapshot.documents.filter { isLegacyDocument(it.data ?: emptyMap()) }
        if (legacyDocs.isEmpty()) return emptyList()

        val adopted = mutableListOf<Adopted>()
        legacyDocs.forEach { doc ->
            val entity = fromLegacyDocument(doc)
            if (entity == null) {
                Log.w(TAG, "Dokumen legacy ${doc.id} tidak bisa dipetakan, dilewati.")
                return@forEach
            }
            runCatching { onAdopt(entity) }
                .onSuccess { adopted += Adopted(doc.reference, newDocIdOf(entity)) }
                .onFailure { Log.w(TAG, "Adopsi gagal untuk ${doc.id}: ${it.message}") }
        }
        Log.i(TAG, "Adopsi ${adopted.size}/${legacyDocs.size} dokumen legacy dari ${collectionRef.id}")
        return adopted
    }

    /**
     * Menghapus dokumen legacy, tetapi hanya setelah dokumen penggantinya terbukti ada
     * di cloud. Yang belum ada pasangannya dilewati agar tidak kehilangan data.
     */
    suspend fun deleteVerified(
        refs: List<Adopted>,
        newCollectionRef: CollectionReference
    ) {
        if (refs.isEmpty()) return
        refs.forEach { (legacyRef, newDocId) ->
            val exists = runCatching {
                withTimeout(CollectionSync.OP_TIMEOUT_MS) {
                    newCollectionRef.document(newDocId).get().await().exists()
                }
            }.getOrElse {
                Log.w(TAG, "Cek dokumen '$newDocId' gagal, legacy tidak dihapus: ${it.message}")
                return@forEach
            }
            if (!exists) {
                Log.w(TAG, "Dokumen '$newDocId' belum ada di cloud, legacy '${legacyRef.id}' disimpan.")
                return@forEach
            }
            runCatching { withTimeout(CollectionSync.OP_TIMEOUT_MS) { legacyRef.delete().await() } }
                .onFailure { Log.w(TAG, "Hapus legacy ${legacyRef.id} gagal: ${it.message}") }
        }
    }
}
