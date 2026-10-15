package com.example.util

import android.content.Context
import android.util.Log
import com.example.data.FinancialRecord
import com.example.data.sync.CollectionSync
import com.example.data.sync.SyncManager
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/**
 * Gerbang autentikasi Google + pintu masuk tunggal ke sinkronisasi.
 *
 * Logika sinkronisasi (aturan konflik, batch, kursor, adopsi data lama) kini ada di
 * [SyncManager]; kelas ini hanya mengurus sesi login dan operasi Sampah yang perlu
 * menyentuh Firestore langsung.
 */
object FirebaseSyncHelper {

    fun isUserSignedIn(): Boolean {
        return FirebaseAuth.getInstance().currentUser != null
    }

    fun getCurrentUserEmail(): String? {
        return FirebaseAuth.getInstance().currentUser?.email
    }

    fun getCurrentUid(): String? {
        return FirebaseAuth.getInstance().currentUser?.uid
    }

    suspend fun signInWithGoogleInFirebase(context: Context, account: GoogleSignInAccount): Result<String> {
        return suspendCancellableCoroutine { continuation ->
            val idToken = account.idToken
            if (idToken == null) {
                continuation.resume(Result.failure(Exception("Google ID Token tidak ditemukan.")))
                return@suspendCancellableCoroutine
            }

            val credential = GoogleAuthProvider.getCredential(idToken, null)
            FirebaseAuth.getInstance().signInWithCredential(credential)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        SyncManager.startRealtime(context)
                        val email = task.result?.user?.email ?: ""
                        continuation.resume(Result.success(email))
                    } else {
                        val errorMsg = task.exception?.localizedMessage ?: "Tidak dapat login ke Firebase."
                        continuation.resume(Result.failure(Exception(errorMsg)))
                    }
                }
        }
    }

    fun signOut() {
        SyncManager.stopRealtime()
        FirebaseAuth.getInstance().signOut()
        // Clear cached bearer token for Gemini proxy
        com.example.data.remote.GeminiClient.clearCachedToken()
    }

    /** Dipanggil setelah setiap tulis lokal: dorong antrean PENDING ke Firestore. */
    suspend fun pushPending(context: Context) = SyncManager.pushPending(context)

    /**
     * Hapus permanen dari Firestore, bukan hanya menandai isDeleted.
     *
     * Dipakai saat Sampah dikosongkan. Kalau hanya isDeleted yang diubah, dokumennya
     * masih tertinggal di cloud dan sync akan menariknya kembali.
     *
     * H2: dokumen HANYA dihapus berdasarkan syncKey-nya sendiri (fallback: id lokal
     * untuk dokumen skema lama yang memang dinamai id baris). Id lokal numerik milik
     * record ini bisa kebetulan sama dengan id dokumen legacy milik transaksi LAIN,
     * jadi id itu tidak boleh ikut dihapus.
     */
    suspend fun purgeRecordsFromFirestore(records: List<FinancialRecord>) {
        val uid = getCurrentUid() ?: return
        if (records.isEmpty()) return
        val db = FirebaseFirestore.getInstance()
        val collection = db.collection("users").document(uid).collection(SyncManager.COLLECTION_RECORDS)

        records.chunked(CollectionSync.BATCH_LIMIT).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { record ->
                val docId = record.syncKey.ifBlank { record.id.toString() }
                if (docId.contains('/')) {
                    Log.w("FirebaseSyncHelper", "Id dokumen '$docId' tidak valid, dilewati.")
                    return@forEach
                }
                batch.delete(collection.document(docId))
            }
            withTimeout(CollectionSync.OP_TIMEOUT_MS) { batch.commit().await() }
        }
    }

    /**
     * Sinkron penuh: transaksi, transaksi berulang, kategori, anggaran, dan pengaturan
     * lewat engine. Kunci keamanan (PIN, biometrik) tidak pernah ikut karena SyncManager
     * hanya memakai allow-list SyncableSettings.
     */
    suspend fun syncFinancialRecordsWithFirestore(context: Context): Result<String> {
        val report = SyncManager.syncAll(context)

        return if (report.isFailure) {
            Result.failure(Exception(report.errors.joinToString("; ")))
        } else {
            val totalApplied =
                report.recordsApplied + report.recurringApplied + report.categoriesApplied +
                    report.budgetsApplied + report.settingsApplied
            val totalPushed =
                report.recordsPushed + report.recurringPushed + report.categoriesPushed +
                    report.budgetsPushed + report.settingsPushed
            Result.success(
                "Penyelarasan berhasil! $totalPushed data terkirim, $totalApplied pembaruan diterima" +
                    (if (report.legacyAdopted > 0) ", ${report.legacyAdopted} data lama dipulihkan" else "") +
                    "."
            )
        }
    }
}