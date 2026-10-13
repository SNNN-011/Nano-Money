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

    /**
     * Identitas lokal untuk dokumen legacy: id dokumennya sendiri.
     *
     * Jalur sync lawas memakai nomor baris Room sebagai id dokumen, jadi id itulah
     * identitas yang sudah dimiliki transaksi tersebut di cloud. Memakai kunci
     * turunan lain (mis. "legacy:12") akan membuat dokumen kedua untuk transaksi
     * yang sama.
     */
    fun legacySyncKey(docId: String): String = docId

    /**
     * Membersihkan dokumen duplikat yang sample versi 6 pernah buat.
     *
     * Migrasi lama menulis dokumen "t:legacy:<id>" padahal dokumen aslinya "<id>"
     * sudah ada, sehingga satu transaksi punya dua dokumen. Setelah kunci lokal
     * dikembalikan ke "<id>" (MIGRATION_6_7), dokumen "t:legacy:*" menjadi sisa:
     * tidak punya pasangan lokal dan akan ditarik balik sebagai duplikat saat pull.
     *
     * Pengaman: dokumen hanya dihapus kalau baris lokal dengan kunci tujuan benar-benar
     * ada. Kalau tidak, dokumen itu satu-satunya salinan dan harus tetap disimpan.
     */
    suspend fun deleteRenamedDuplicates(
        collectionRef: CollectionReference,
        prefix: String,
        hasLocalKey: suspend (String) -> Boolean
    ): Int {
        val snapshot = withTimeout(CollectionSync.OP_TIMEOUT_MS) { collectionRef.get().await() }
        val orphans = snapshot.documents.filter { it.id.startsWith(prefix) }
        if (orphans.isEmpty()) return 0

        var deleted = 0
        orphans.forEach { doc ->
            val target = doc.id.removePrefix(prefix)
            val hasLocal = runCatching { hasLocalKey(target) }.getOrDefault(false)
            if (!hasLocal) {
                Log.w(TAG, "Dokumen '${doc.id}' tidak punya pasangan lokal '$target', disimpan.")
                return@forEach
            }
            runCatching { withTimeout(CollectionSync.OP_TIMEOUT_MS) { doc.reference.delete().await() } }
                .onSuccess { deleted++ }
                .onFailure { Log.w(TAG, "Hapus duplikat ${doc.id} gagal: ${it.message}") }
        }
        Log.i(TAG, "Menghapus $deleted dokumen duplikat berawalan '$prefix'")
        return deleted
    }

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
     *
     * Kalau id dokumen pengganti sama dengan id dokumen legacy, itu berarti dokumen
     * yang sama diperkaya di tempat (identitasnya memang id itu), jadi tidak dihapus.
     */
    suspend fun deleteVerified(
        refs: List<Adopted>,
        newCollectionRef: CollectionReference
    ) {
        if (refs.isEmpty()) return
        refs.forEach { (legacyRef, newDocId) ->
            if (legacyRef.id == newDocId) return@forEach
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
