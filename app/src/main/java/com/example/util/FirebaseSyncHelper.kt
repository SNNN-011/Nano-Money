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
            "notes" to record.notes,
            "isDeleted" to record.isDeleted
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
                .set(hashMapOf("isDeleted" to true), com.google.firebase.firestore.SetOptions.merge())
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
            val localRecords = dao.getAllRecordsWithDeleted().first()
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
                    val idObj = doc.get("id")
                    val id = (idObj as? Number)?.toInt() ?: doc.id.toIntOrNull() ?: return@mapNotNull null
                    
                    val amountObj = doc.get("amount")
                    val amount = (amountObj as? Number)?.toDouble() ?: 0.0
                    
                    val dateObj = doc.get("date")
                    val date = (dateObj as? Number)?.toLong() ?: 0L
                    
                    id to FinancialRecord(
                        id = id,
                        description = doc.getString("description") ?: "",
                        amount = amount,
                        type = doc.getString("type") ?: "",
                        category = doc.getString("category") ?: "",
                        date = date,
                        notes = doc.getString("notes") ?: "",
                        isDeleted = doc.getBoolean("isDeleted") ?: false
                    )
                } catch (e: Exception) {
                    null
                }
            }.toMap()

            val localRecordsMap = localRecords.associateBy { it.id }
            var uploadedCount = 0
            var downloadedCount = 0

            val recordsToWrite = mutableListOf<Pair<String, Map<String, Any>>>()

            for (localRecord in localRecords) {
                val firestoreRecord = firestoreRecordsMap[localRecord.id]
                if (localRecord.isDeleted) {
                    if (firestoreRecord == null || !firestoreRecord.isDeleted) {
                        val docData = hashMapOf<String, Any>(
                            "id" to localRecord.id,
                            "description" to localRecord.description,
                            "amount" to localRecord.amount,
                            "type" to localRecord.type,
                            "category" to localRecord.category,
                            "date" to localRecord.date,
                            "notes" to localRecord.notes,
                            "isDeleted" to true
                        )
                        recordsToWrite.add(localRecord.id.toString() to docData)
                    }
                } else {
                    if (firestoreRecord != null && firestoreRecord.isDeleted) {
                        dao.updateRecord(localRecord.copy(isDeleted = true))
                    } else if (firestoreRecord == null || firestoreRecord != localRecord) {
                        val docData = hashMapOf<String, Any>(
                            "id" to localRecord.id,
                            "description" to localRecord.description,
                            "amount" to localRecord.amount,
                            "type" to localRecord.type,
                            "category" to localRecord.category,
                            "date" to localRecord.date,
                            "notes" to localRecord.notes,
                            "isDeleted" to false
                        )
                        recordsToWrite.add(localRecord.id.toString() to docData)
                    }
                }
            }

            if (recordsToWrite.isNotEmpty()) {
                val chunks = recordsToWrite.chunked(500)
                for (chunk in chunks) {
                    suspendCancellableCoroutine<Unit> { continuation ->
                        val batch = db.batch()
                        for ((docId, data) in chunk) {
                            val docRef = collectionRef.document(docId)
                            batch.set(docRef, data)
                        }
                        batch.commit().addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                uploadedCount += chunk.size
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
                    val idObj = doc.get("id")
                    val id = (idObj as? Number)?.toInt() ?: doc.id.toIntOrNull() ?: return@mapNotNull null
                    
                    val amountObj = doc.get("amount")
                    val amount = (amountObj as? Number)?.toDouble() ?: 0.0
                    
                    val dayOfMonthObj = doc.get("dayOfMonth")
                    val dayOfMonth = (dayOfMonthObj as? Number)?.toInt() ?: 1
                    
                    val lastRunDateObj = doc.get("lastRunDate")
                    val lastRunDate = (lastRunDateObj as? Number)?.toLong()
                    
                    id to com.example.data.RecurringTransaction(
                        id = id,
                        description = doc.getString("description") ?: "",
                        amount = amount,
                        type = doc.getString("type") ?: "",
                        category = doc.getString("category") ?: "",
                        dayOfMonth = dayOfMonth,
                        notes = doc.getString("notes") ?: "",
                        lastRunDate = lastRunDate,
                        isActive = doc.getBoolean("isActive") ?: true
                    )
                } catch (e: Exception) {
                    null
                }
            }.toMap()

            val localRecurringMap = localRecurring.associateBy { it.id }

            val recurringToWrite = mutableListOf<Pair<String, Map<String, Any?>>>()

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
                    recurringToWrite.add(localRec.id.toString() to docData)
                }
            }

            if (recurringToWrite.isNotEmpty()) {
                val chunks = recurringToWrite.chunked(500)
                for (chunk in chunks) {
                    suspendCancellableCoroutine<Unit> { continuation ->
                        val batch = db.batch()
                        for ((docId, data) in chunk) {
                            val docRef = recurringCollectionRef.document(docId)
                            batch.set(docRef, data)
                        }
                        batch.commit().addOnCompleteListener { continuation.resume(Unit) }
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

            // 3. Sync Settings / Preferences (financial_tracker_prefs, security_prefs)
            val prefNames = listOf("financial_tracker_prefs", "security_prefs")
            
            for (prefName in prefNames) {
                val prefs = com.example.util.SecurePrefsHelper.getEncryptedPrefs(context, prefName)
                val settingsDocRef = userDocRef.collection("settings").document(prefName)
                
                val settingsSnapshot = suspendCancellableCoroutine { continuation ->
                    settingsDocRef.get().addOnCompleteListener { task ->
                        if (task.isSuccessful) continuation.resume(task.result)
                        else continuation.resume(null)
                    }
                }

                val firestoreSettingsMap = settingsSnapshot?.data ?: emptyMap<String, Any>()
                val localSettingsMap = prefs.all

                val mergedSettings = mutableMapOf<String, Any>()
                mergedSettings.putAll(firestoreSettingsMap)
                
                for ((k, v) in localSettingsMap) {
                    if (v != null) {
                        if (v is Set<*>) {
                            mergedSettings[k] = v.toList()
                        } else {
                            mergedSettings[k] = v
                        }
                    }
                }

                
                suspendCancellableCoroutine<Unit> { continuation ->
                    settingsDocRef.set(mergedSettings).addOnCompleteListener { continuation.resume(Unit) }
                }
                
                val editor = prefs.edit()
                for ((key, value) in mergedSettings) {
                    when (value) {
                        is String -> editor.putString(key, value)
                        is Boolean -> editor.putBoolean(key, value)
                        is Long -> editor.putLong(key, value)
                        is Float -> editor.putFloat(key, value)
                        is Double -> editor.putFloat(key, value.toFloat())
                        is Int -> editor.putInt(key, value)
                        is Set<*> -> {
                            val stringSet = value.mapNotNull { it?.toString() }.toSet()
                            editor.putStringSet(key, stringSet)
                        }
                        is List<*> -> {
                            val stringSet = value.mapNotNull { it?.toString() }.toSet()
                            editor.putStringSet(key, stringSet)
                        }
                    }
                }
                editor.apply()
            }

            Result.success("Penyelarasan berhasil dinormalisasi! Seluruh data (Transaksi, Rutin, & Pengaturan) telah disinkronkan.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
