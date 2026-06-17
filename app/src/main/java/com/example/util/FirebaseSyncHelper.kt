package com.example.util

import android.content.Context
import com.example.data.AppDatabase
import com.example.data.FinancialRecord
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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
        FirebaseAuth.getInstance().signOut()
    }

    suspend fun uploadRecordToFirestoreDirectly(record: FinancialRecord) {
        val uid = getCurrentUid() ?: return
        val db = FirebaseFirestore.getInstance()
        val docData = hashMapOf(
            "id" to record.id,
            "description" to record.description,
            "amount" to record.amount,
            "type" to record.type,
            "category" to record.category,
            "date" to record.date,
            "notes" to record.notes
        )

        suspendCancellableCoroutine<Unit> { continuation ->
            db.collection("users").document(uid).collection("financial_records")
                .document(record.id.toString())
                .set(docData)
                .addOnCompleteListener {
                    continuation.resume(Unit)
                }
        }
    }

    suspend fun deleteRecordFromFirestoreDirectly(recordId: Int) {
        val uid = getCurrentUid() ?: return
        val db = FirebaseFirestore.getInstance()

        suspendCancellableCoroutine<Unit> { continuation ->
            db.collection("users").document(uid).collection("financial_records")
                .document(recordId.toString())
                .delete()
                .addOnCompleteListener {
                    continuation.resume(Unit)
                }
        }
    }

    suspend fun syncFinancialRecordsWithFirestore(context: Context): Result<String> {
        val uid = getCurrentUid()
            ?: return Result.failure(Exception("Silakan hubungkan akun Google Anda terlebih dahulu untuk penyelarasan cloud."))

        val db = FirebaseFirestore.getInstance()
        val collectionRef = db.collection("users").document(uid).collection("financial_records")

        val appDb = AppDatabase.getDatabase(context)
        val dao = appDb.financialRecordDao()

        return try {
            val localRecords = dao.getAllRecords().first()

            val firestoreSnapshot = suspendCancellableCoroutine { continuation ->
                collectionRef.get().addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        continuation.resume(task.result)
                    } else {
                        continuation.resumeWithException(task.exception ?: Exception("Gagal mengambil data dari Google Firestore."))
                    }
                }
            }

            val firestoreDocs = firestoreSnapshot?.documents ?: emptyList()

            val firestoreRecordsMap = firestoreDocs.mapNotNull { doc ->
                try {
                    val id = doc.getLong("id")?.toInt() ?: return@mapNotNull null
                    val description = doc.getString("description") ?: ""
                    val amount = doc.getDouble("amount") ?: 0.0
                    val type = doc.getString("type") ?: ""
                    val category = doc.getString("category") ?: ""
                    val date = doc.getLong("date") ?: 0L
                    val notes = doc.getString("notes") ?: ""
                    id to FinancialRecord(
                        id = id,
                        description = description,
                        amount = amount,
                        type = type,
                        category = category,
                        date = date,
                        notes = notes
                    )
                } catch (e: Exception) {
                    null
                }
            }.toMap()

            val localRecordsMap = localRecords.associateBy { it.id }

            var uploadedCount = 0
            var downloadedCount = 0

            for (localRecord in localRecords) {
                val firestoreRecord = firestoreRecordsMap[localRecord.id]
                if (firestoreRecord == null || firestoreRecord != localRecord) {
                    val docData = hashMapOf(
                        "id" to localRecord.id,
                        "description" to localRecord.description,
                        "amount" to localRecord.amount,
                        "type" to localRecord.type,
                        "category" to localRecord.category,
                        "date" to localRecord.date,
                        "notes" to localRecord.notes
                    )

                    suspendCancellableCoroutine<Unit> { continuation ->
                        collectionRef.document(localRecord.id.toString()).set(docData)
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    uploadedCount++
                                }
                                continuation.resume(Unit)
                            }
                    }
                }
            }

            val recordsToInsertLocally = mutableListOf<FinancialRecord>()
            for ((id, fsRecord) in firestoreRecordsMap) {
                if (!localRecordsMap.containsKey(id)) {
                    recordsToInsertLocally.add(fsRecord)
                    downloadedCount++
                }
            }

            if (recordsToInsertLocally.isNotEmpty()) {
                dao.insertAll(recordsToInsertLocally)
            }

            Result.success("Penyelarasan berhasil dinormalisasi! $uploadedCount data baru dicadangkan ke cloud. $downloadedCount data diunduh ke penyimpanan lokal.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
