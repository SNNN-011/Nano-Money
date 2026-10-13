package com.example.data.sync

import android.content.Context
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.FinancialRecord
import com.example.data.FinancialRecordRepository
import com.example.data.RecurringTransaction
import com.example.data.RecurringTransactionRepository
import com.example.data.SyncStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Otak sinkronisasi: push + pull tiap koleksi secara terpisah, plus adopsi sekali
 * untuk dokumen cloud yang masih memakai skema lama.
 *
 * Koleksi berjalan independen: kegagalan satu tidak menghentikan yang lain, dan
 * kursor hanya maju bila pull koleksi itu sukses.
 */
object SyncManager {

    private const val TAG = "SyncManager"
    const val COLLECTION_RECORDS = "financial_records"
    const val COLLECTION_RECURRING = "recurring_transactions"

    data class Report(
        val recordsPushed: Int = 0,
        val recordsApplied: Int = 0,
        val recurringPushed: Int = 0,
        val recurringApplied: Int = 0,
        val legacyAdopted: Int = 0,
        val errors: List<String> = emptyList()
    ) {
        val isFailure: Boolean get() = errors.isNotEmpty()
    }

    /**
     * Dipanggil setelah setiap tulis lokal (tambah/edit/hapus transaksi atau recurring).
     * Kegagalan diamkan karena baris tetap PENDING dan akan dikirim pada sinkron penuh.
     */
    suspend fun pushPending(context: Context) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val recordRepo = FinancialRecordRepository(db.financialRecordDao())
        val recurringRepo = RecurringTransactionRepository(db.recurringTransactionDao())

        runCatching { recordsSync(recordRepo).push() }
            .onFailure { Log.w(TAG, "Push transaksi gagal: ${it.message}") }

        runCatching { recurringSync(recurringRepo).push() }
            .onFailure { Log.w(TAG, "Push transaksi berulang gagal: ${it.message}") }
    }

    /** Sinkron penuh: push + pull (dan sekali saja adopsi dokumen lama). */
    suspend fun syncAll(context: Context): Report = withContext(Dispatchers.IO) {
        if (FirebaseAuth.getInstance().currentUser == null) {
            return@withContext Report(errors = listOf("Belum terhubung akun Google."))
        }

        val db = AppDatabase.getDatabase(context)
        val recordRepo = FinancialRecordRepository(db.financialRecordDao())
        val recurringRepo = RecurringTransactionRepository(db.recurringTransactionDao())
        val errors = mutableListOf<String>()

        // --- Adopsi dokumen legacy (sekali per koleksi) ---
        var adopted = 0
        val legacyRefs = mutableListOf<com.google.firebase.firestore.DocumentReference>()
        if (!SyncCursors.isAdopted(context, COLLECTION_RECORDS)) {
            runCatching {
                legacyRefs += LegacyAdoption.adopt(
                    collectionRef = userCollection(COLLECTION_RECORDS),
                    fromLegacyDocument = ::mapLegacyRecord,
                    onAdopt = { entity ->
                        recordRepo.applyRemote(entity)
                        adopted++
                    }
                )
                SyncCursors.markAdopted(context, COLLECTION_RECORDS)
            }.onFailure { errors += "Adopsi data lama: ${it.localizedMessage ?: "gagal"}" }
        }

        // --- Transaksi ---
        var recordsPushed = 0
        var recordsApplied = 0
        runCatching { recordsPushed = recordsSync(recordRepo).push().pushed }
            .onFailure { errors += "Transaksi: ${it.localizedMessage ?: "gagal push"}" }
        runCatching {
            val result = recordsSync(recordRepo).pull(SyncCursors.get(context, COLLECTION_RECORDS))
            SyncCursors.set(context, COLLECTION_RECORDS, result.newestUpdatedAt)
            recordsApplied = result.applied
        }.onFailure { errors += "Transaksi: ${it.localizedMessage ?: "gagal pull"}" }

        // --- Transaksi berulang ---
        var recurringPushed = 0
        var recurringApplied = 0
        runCatching { recurringPushed = recurringSync(recurringRepo).push().pushed }
            .onFailure { errors += "Transaksi berulang: ${it.localizedMessage ?: "gagal push"}" }
        runCatching {
            val result = recurringSync(recurringRepo).pull(SyncCursors.get(context, COLLECTION_RECURRING))
            SyncCursors.set(context, COLLECTION_RECURRING, result.newestUpdatedAt)
            recurringApplied = result.applied
        }.onFailure { errors += "Transaksi berulang: ${it.localizedMessage ?: "gagal pull"}" }

        // Dokumen legacy dihapus setelah versi barunya sudah aman di cloud.
        if (legacyRefs.isNotEmpty()) {
            runCatching { LegacyAdoption.deleteDocs(legacyRefs) }
                .onFailure { Log.w(TAG, "Bersihkan dokumen legacy gagal: ${it.message}") }
        }

        Report(
            recordsPushed = recordsPushed,
            recordsApplied = recordsApplied,
            recurringPushed = recurringPushed,
            recurringApplied = recurringApplied,
            legacyAdopted = adopted,
            errors = errors
        )
    }

    private fun userCollection(name: String) =
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(FirebaseAuth.getInstance().currentUser?.uid.orEmpty())
            .collection(name)

    private fun recordsSync(repo: FinancialRecordRepository): CollectionSync<FinancialRecord> {
        val ref = userCollection(COLLECTION_RECORDS)

        return CollectionSync(
            name = COLLECTION_RECORDS,
            collectionRef = ref,
            pendingItems = { repo.getPendingSync() },
            findLocal = { repo.findBySyncKey(it) },
            applyRemote = { repo.applyRemote(it) },
            markSynced = { repo.markSynced(it) },
            toDocument = { record ->
                mapOf(
                    CollectionSync.FIELD_SYNC_KEY to record.syncKey,
                    "description" to record.description,
                    "amount" to record.amount,
                    "type" to record.type,
                    "category" to record.category,
                    "categoryId" to record.categoryId,
                    "date" to record.date,
                    "notes" to record.notes,
                    CollectionSync.FIELD_UPDATED_AT to record.updatedAt,
                    CollectionSync.FIELD_IS_DELETED to record.isDeleted
                )
            },
            fromDocument = ::mapRemoteRecord
        )
    }

    private fun recurringSync(repo: RecurringTransactionRepository): CollectionSync<RecurringTransaction> {
        val ref = userCollection(COLLECTION_RECURRING)

        return CollectionSync(
            name = COLLECTION_RECURRING,
            collectionRef = ref,
            pendingItems = { repo.getPendingSync() },
            findLocal = { repo.findBySyncKey(it) },
            applyRemote = { repo.applyRemote(it) },
            markSynced = { repo.markSynced(it) },
            toDocument = { item ->
                mapOf(
                    CollectionSync.FIELD_SYNC_KEY to item.syncKey,
                    "description" to item.description,
                    "amount" to item.amount,
                    "type" to item.type,
                    "category" to item.category,
                    "dayOfMonth" to item.dayOfMonth,
                    "notes" to item.notes,
                    "lastRunDate" to item.lastRunDate,
                    "isActive" to item.isActive,
                    CollectionSync.FIELD_UPDATED_AT to item.updatedAt,
                    CollectionSync.FIELD_IS_DELETED to item.isDeleted
                )
            },
            fromDocument = ::mapRemoteRecurring
        )
    }

    private fun mapRemoteRecord(doc: DocumentSnapshot): FinancialRecord? {
        val updatedAt = (doc.get(CollectionSync.FIELD_UPDATED_AT) as? Number)?.toLong() ?: return null
        return FinancialRecord(
            id = 0,
            description = doc.getString("description").orEmpty(),
            amount = (doc.get("amount") as? Number)?.toLong() ?: 0L,
            type = doc.getString("type").orEmpty(),
            category = doc.getString("category").orEmpty(),
            categoryId = doc.getString("categoryId").orEmpty(),
            date = (doc.get("date") as? Number)?.toLong() ?: 0L,
            notes = doc.getString("notes").orEmpty(),
            isDeleted = doc.getBoolean(CollectionSync.FIELD_IS_DELETED) ?: false,
            updatedAt = updatedAt,
            syncKey = doc.getString(CollectionSync.FIELD_SYNC_KEY) ?: doc.id,
            syncStatus = SyncStatus.SYNCED
        )
    }

    private fun mapRemoteRecurring(doc: DocumentSnapshot): RecurringTransaction? {
        val updatedAt = (doc.get(CollectionSync.FIELD_UPDATED_AT) as? Number)?.toLong() ?: return null
        return RecurringTransaction(
            id = 0,
            description = doc.getString("description").orEmpty(),
            amount = (doc.get("amount") as? Number)?.toLong() ?: 0L,
            type = doc.getString("type").orEmpty(),
            category = doc.getString("category").orEmpty(),
            dayOfMonth = (doc.get("dayOfMonth") as? Number)?.toInt() ?: 1,
            notes = doc.getString("notes").orEmpty(),
            isDeleted = doc.getBoolean(CollectionSync.FIELD_IS_DELETED) ?: false,
            lastRunDate = (doc.get("lastRunDate") as? Number)?.toLong(),
            isActive = doc.getBoolean("isActive") ?: true,
            updatedAt = updatedAt,
            syncKey = doc.getString(CollectionSync.FIELD_SYNC_KEY) ?: doc.id,
            syncStatus = SyncStatus.SYNCED
        )
    }

    /**
     * Dokumen lama (tanpa updatedAt/syncKey) diubah jadi entitas dengan kunci
     * "legacy:<docId>". updatedAt memakai waktu adopsi supaya record ini tetap
     * ikut mengalir ke perangkat lain.
     */
    private fun mapLegacyRecord(doc: DocumentSnapshot): FinancialRecord? {
        val amount = (doc.get("amount") as? Number)?.toLong() ?: return null
        if (amount <= 0L) return null
        return FinancialRecord(
            id = 0,
            description = doc.getString("description").orEmpty(),
            amount = amount,
            type = doc.getString("type").orEmpty(),
            category = doc.getString("category").orEmpty(),
            categoryId = doc.getString("categoryId").orEmpty(),
            date = (doc.get("date") as? Number)?.toLong() ?: 0L,
            notes = doc.getString("notes").orEmpty(),
            isDeleted = doc.getBoolean(CollectionSync.FIELD_IS_DELETED) ?: false,
            updatedAt = System.currentTimeMillis(),
            syncKey = LegacyAdoption.legacySyncKey(doc.id),
            syncStatus = SyncStatus.PENDING
        )
    }

    // ---- Realtime (snapshot listener) ----

    private val realtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val registrations = CopyOnWriteArrayList<com.google.firebase.firestore.ListenerRegistration>()
    private val realtimeOn = AtomicBoolean(false)

    /**
     * Menyalakan listener realtime untuk semua koleksi.
     * Aman dipanggil berulang; hanya listener pertama yang benar-benar menempel.
     */
    fun startRealtime(context: Context) {
        if (FirebaseAuth.getInstance().currentUser == null) return
        if (!realtimeOn.compareAndSet(false, true)) return

        val appContext = context.applicationContext
        val db = AppDatabase.getDatabase(appContext)
        val syncs = listOf(
            recordsSync(FinancialRecordRepository(db.financialRecordDao())),
            recurringSync(RecurringTransactionRepository(db.recurringTransactionDao()))
        )

        syncs.forEach { sync ->
            val registration = sync.collection().addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Realtime ${sync.name} bermasalah: ${error.message}")
                    return@addSnapshotListener
                }
                val documents = snapshot?.documents ?: return@addSnapshotListener
                realtimeScope.launch(Dispatchers.IO) {
                    runCatching { sync.applyRemoteDocuments(documents) }
                        .onFailure { Log.w(TAG, "Realtime ${sync.name} gagal apply: ${it.message}") }
                }
            }
            if (registration != null) registrations.add(registration)
        }
    }

    /** Melepas semua listener (dipakai saat logout). */
    fun stopRealtime() {
        registrations.forEach { runCatching { it.remove() } }
        registrations.clear()
        realtimeOn.set(false)
    }
}