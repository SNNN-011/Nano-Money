package com.example.util

import android.content.Context
import com.example.data.FinancialRecord
import com.example.data.sync.SyncManager
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
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
     * Dokumen dihapus berdasarkan syncKey; id numerik lama tetap ditangani agar
     * dokumen dari skema sebelumnya ikut bersih.
     */
    suspend fun purgeRecordsFromFirestore(records: List<FinancialRecord>) {
        val uid = getCurrentUid() ?: return
        if (records.isEmpty()) return
        val db = FirebaseFirestore.getInstance()
        val collection = db.collection("users").document(uid).collection(SyncManager.COLLECTION_RECORDS)

        suspendCancellableCoroutine<Unit> { continuation ->
            val batch = db.batch()
            records.forEach { record ->
                // Dokumen versi lama memakai id numerik sebagai id dokumen.
                batch.delete(collection.document(record.syncKey.ifBlank { record.id.toString() }))
                if (record.syncKey.isNotBlank()) {
                    batch.delete(collection.document(record.id.toString()))
                }
            }
            batch.commit().addOnCompleteListener { continuation.resume(Unit) }
        }
    }

    /**
     * Sinkron penuh: transaksi + transaksi berulang lewat engine, lalu sinkron
     * kategori & preferensi lawas (akan digantikan tabel categories/app_settings).
     */
    suspend fun syncFinancialRecordsWithFirestore(context: Context): Result<String> {
        val report = SyncManager.syncAll(context)
        runCatching { syncCategoriesAndPreferences(context) }

        return if (report.isFailure) {
            Result.failure(Exception(report.errors.joinToString("; ")))
        } else {
            Result.success(
                "Penyelarasan berhasil! ${report.recordsPushed} transaksi & " +
                    "${report.recurringPushed} transaksi berulang terkirim, " +
                    "${report.recordsApplied + report.recurringApplied} pembaruan diterima" +
                    (if (report.legacyAdopted > 0) ", ${report.legacyAdopted} data lama dipulihkan" else "") +
                    "."
            )
        }
    }

    /**
     * Sinkron kategori & preferensi versi lama (masih berbasis SharedPreferences).
     * Bertahan sampai digantikan tabel categories dan app_settings.
     */
    private suspend fun syncCategoriesAndPreferences(context: Context) {
        val uid = getCurrentUid() ?: return
        val db = FirebaseFirestore.getInstance()
        val settingsRef = db.collection("users").document(uid)
            .collection("settings").document("financial_tracker_prefs")
        val prefs = SecurePrefsHelper.getEncryptedPrefs(context, "financial_tracker_prefs")

        val remote = runCatching { settingsRef.get().await().data ?: emptyMap() }.getOrDefault(emptyMap())

        val categoryKeys = listOf("income_categories_list", "expense_categories_list")
        val defaultCategories = mapOf(
            "income_categories_list" to "Gaji,Investasi,Freelance,Lainnya",
            "expense_categories_list" to "Makanan,Transportasi,Tagihan,Hiburan,Belanja,Lainnya"
        )
        val skipKeys = categoryKeys + setOf("pin_hash", "pin_salt", "pin_enabled", "saved_pin")

        val merged = mutableMapOf<String, Any>()
        // Kategori: kalau user sudah pernah mengubahnya di perangkat ini, lokal yang menang.
        categoryKeys.forEach { key ->
            val localValue = prefs.getString(key, null)
            val remoteValue = remote[key] as? String
            val isLocalDefault = localValue.isNullOrBlank() || localValue == defaultCategories[key]
            when {
                !isLocalDefault -> merged[key] = localValue!!
                !remoteValue.isNullOrBlank() -> {
                    merged[key] = remoteValue
                    prefs.edit().putString(key, remoteValue).apply()
                }
                !localValue.isNullOrBlank() -> merged[key] = localValue
            }
        }
        remote.forEach { (k, v) -> if (k !in skipKeys) merged[k] = v }
        prefs.all.forEach { (k, v) -> if (v != null && k !in skipKeys) merged[k] = v }

        runCatching { settingsRef.set(merged).await() }
    }
}