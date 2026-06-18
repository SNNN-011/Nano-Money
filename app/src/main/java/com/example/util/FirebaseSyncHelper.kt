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
        val userDocRef = db.collection("users").document(uid)
        val collectionRef = userDocRef.collection("financial_records")
        val recurringCollectionRef = userDocRef.collection("recurring_transactions")

        val appDb = AppDatabase.getDatabase(context)
        val dao = appDb.financialRecordDao()
        val recurringDao = appDb.recurringTransactionDao()

        return try {
            val localRecords = dao.getAllRecords().first()
            val localRecurring = recurringDao.getAllRecurring().first()

            // 1. Sync Financial Records
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
                    id to FinancialRecord(
                        id = id,
                        description = doc.getString("description") ?: "",
                        amount = doc.getDouble("amount") ?: 0.0,
                        type = doc.getString("type") ?: "",
                        category = doc.getString("category") ?: "",
                        date = doc.getLong("date") ?: 0L,
                        notes = doc.getString("notes") ?: ""
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
                                if (task.isSuccessful) uploadedCount++
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

            // 2. Sync Recurring Transactions
            val recurringSnapshot = suspendCancellableCoroutine { continuation ->
                recurringCollectionRef.get().addOnCompleteListener { task ->
                    if (task.isSuccessful) continuation.resume(task.result)
                    else continuation.resume(null) // ignore error for recurring to not block
                }
            }
            
            val recurringDocs = recurringSnapshot?.documents ?: emptyList()
            val firestoreRecurringMap = recurringDocs.mapNotNull { doc ->
                try {
                    val id = doc.getLong("id")?.toInt() ?: return@mapNotNull null
                    id to com.example.data.RecurringTransaction(
                        id = id,
                        description = doc.getString("description") ?: "",
                        amount = doc.getDouble("amount") ?: 0.0,
                        type = doc.getString("type") ?: "",
                        category = doc.getString("category") ?: "",
                        dayOfMonth = doc.getLong("dayOfMonth")?.toInt() ?: 1,
                        notes = doc.getString("notes") ?: "",
                        lastRunDate = doc.getLong("lastRunDate"),
                        isActive = doc.getBoolean("isActive") ?: true
                    )
                } catch (e: Exception) {
                    null
                }
            }.toMap()

            val localRecurringMap = localRecurring.associateBy { it.id }

            for (localRec in localRecurring) {
                val firestoreRec = firestoreRecurringMap[localRec.id]
                if (firestoreRec == null || firestoreRec != localRec) {
                    val docData = hashMapOf(
                        "id" to localRec.id,
                        "description" to localRec.description,
                        "amount" to localRec.amount,
                        "type" to localRec.type,
                        "category" to localRec.category,
                        "dayOfMonth" to localRec.dayOfMonth,
                        "notes" to localRec.notes,
                        "lastRunDate" to localRec.lastRunDate,
                        "isActive" to localRec.isActive
                    )
                    suspendCancellableCoroutine<Unit> { continuation ->
                        recurringCollectionRef.document(localRec.id.toString()).set(docData).addOnCompleteListener { continuation.resume(Unit) }
                    }
                }
            }

            val recurringToInsertLocally = mutableListOf<com.example.data.RecurringTransaction>()
            for ((id, fsRec) in firestoreRecurringMap) {
                if (!localRecurringMap.containsKey(id)) {
                    recurringToInsertLocally.add(fsRec)
                }
            }
            if (recurringToInsertLocally.isNotEmpty()) {
                recurringDao.insertAll(recurringToInsertLocally)
            }

            // 3. Sync Settings / Preferences (financial_tracker_prefs)
            val prefs = context.getSharedPreferences("financial_tracker_prefs", Context.MODE_PRIVATE)
            val settingsDocRef = userDocRef.collection("settings").document("app_prefs")
            
            val settingsSnapshot = suspendCancellableCoroutine { continuation ->
                settingsDocRef.get().addOnCompleteListener { task ->
                    if (task.isSuccessful) continuation.resume(task.result)
                    else continuation.resume(null)
                }
            }

            val firestoreSettingsMap = settingsSnapshot?.data ?: emptyMap<String, Any>()
            val localSettingsMap = prefs.all

            // Merge logic: if local has something different, we could upload it, or if firestore has something newer.
            // A simple strategy is to push local to firestore, EXCEPT for settings that only exist in firestore (which we pull).
            // Since SharedPreferences lacks granular timestamps, we will push all local keys to firestore, and pull any missing keys.
            val mergedSettings = mutableMapOf<String, Any>()
            mergedSettings.putAll(firestoreSettingsMap)
            mergedSettings.putAll(localSettingsMap.filterValues { it != null } as Map<String, Any>)
            
            // Push merged to firestore
            suspendCancellableCoroutine<Unit> { continuation ->
                settingsDocRef.set(mergedSettings).addOnCompleteListener { continuation.resume(Unit) }
            }
            
            // Apply merged settings to local
            val editor = prefs.edit()
            for ((key, value) in mergedSettings) {
                when (value) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Double -> editor.putFloat(key, value.toFloat())
                    is Int -> editor.putInt(key, value)
                }
            }
            editor.apply()

            Result.success("Penyelarasan berhasil dinormalisasi! Seluruh data (Transaksi, Rutin, & Pengaturan) telah disinkronkan.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
