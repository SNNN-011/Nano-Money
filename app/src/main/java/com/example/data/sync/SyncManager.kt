package com.example.data.sync

import android.content.Context
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.AppSetting
import com.example.data.AppSettingsRepository
import com.example.data.Category
import com.example.data.CategoryBudget
import com.example.data.CategoryBudgetRepository
import com.example.data.CategoryRepository
import com.example.data.FinancialRecord
import com.example.data.FinancialRecordRepository
import com.example.data.RecurringTransaction
import com.example.data.RecurringTransactionRepository
import com.example.data.SyncStatus
import com.example.data.SyncableSettings
import com.example.util.SecurePrefsHelper
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
    const val COLLECTION_CATEGORIES = "categories"
    const val COLLECTION_CATEGORY_BUDGETS = "category_budgets"
    const val COLLECTION_SETTINGS = "settings"

    /** Awalan kunci yang pernah dipakai migrasi v6 dan menyisakan dokumen duplikat. */
    const val LEGACY_KEY_PREFIX = "t:legacy:"

    /** Penanda pembersihan dokumen duplikat, dipakai sekali seumur device. */
    const val CLEANUP_RENAMED_FLAG = "cleanup_renamed_v1"

    data class Report(
        val recordsPushed: Int = 0,
        val recordsApplied: Int = 0,
        val recurringPushed: Int = 0,
        val recurringApplied: Int = 0,
        val categoriesPushed: Int = 0,
        val categoriesApplied: Int = 0,
        val budgetsPushed: Int = 0,
        val budgetsApplied: Int = 0,
        val settingsPushed: Int = 0,
        val settingsApplied: Int = 0,
        val legacyAdopted: Int = 0,
        val errors: List<String> = emptyList()
    ) {
        val isFailure: Boolean get() = errors.isNotEmpty()
    }

    private fun trackerPrefs(context: Context) =
        SecurePrefsHelper.getEncryptedPrefs(context, "financial_tracker_prefs")

    /**
     * Dipanggil setelah setiap tulis lokal (tambah/edit/hapus transaksi, recurring,
     * kategori, anggaran, atau pengaturan). Kegagalan diamkan karena baris tetap PENDING.
     */
    suspend fun pushPending(context: Context) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val prefs = trackerPrefs(context)
        val syncs = allSyncs(
            db,
            FinancialRecordRepository(db.financialRecordDao()),
            RecurringTransactionRepository(db.recurringTransactionDao()),
            CategoryRepository(db.categoryDao(), prefs),
            CategoryBudgetRepository(db.categoryBudgetDao()),
            AppSettingsRepository(db.appSettingDao())
        )

        syncs.forEach { sync ->
            runCatching { sync.push() }
                .onFailure { Log.w(TAG, "Push ${sync.name} gagal: ${it.message}") }
        }
    }

    /** Sinkron penuh: push + pull tiap koleksi, plus sekali saja adopsi dokumen lama. */
    suspend fun syncAll(context: Context): Report = withContext(Dispatchers.IO) {
        if (FirebaseAuth.getInstance().currentUser == null) {
            return@withContext Report(errors = listOf("Belum terhubung akun Google."))
        }

        val db = AppDatabase.getDatabase(context)
        val prefs = trackerPrefs(context)
        val recordRepo = FinancialRecordRepository(db.financialRecordDao())
        val recurringRepo = RecurringTransactionRepository(db.recurringTransactionDao())
        val categoryRepo = CategoryRepository(db.categoryDao(), prefs)
        val budgetRepo = CategoryBudgetRepository(db.categoryBudgetDao())
        val settingsRepo = AppSettingsRepository(db.appSettingDao())
        val errors = mutableListOf<String>()

        // --- Adopsi dokumen legacy (sekali per koleksi) ---
        // Flag "adopted" baru ditulis setelah push + verifikasi +
        // pembersihan selesai, supaya kegagalan tidak membuat data lama
        // terlewat selamanya.
        var adopted = 0
        val legacyRefs = mutableListOf<LegacyAdoption.Adopted>()
        var legacyPending = !SyncCursors.isAdopted(context, COLLECTION_RECORDS)
        if (legacyPending) {
            runCatching {
                legacyRefs += LegacyAdoption.adopt(
                    collectionRef = userCollection(COLLECTION_RECORDS),
                    fromLegacyDocument = ::mapLegacyRecord,
                    onAdopt = { entity ->
                        // Id dokumen legacy = syncKey, jadi baris yang sama bisa sudah
                        // ada di lokal. applyRemote menimpa apa adanya, jadi aturan
                        // konflik wajib dicek lebih dulu supaya versi lokal yang lebih
                        // baru tidak ditimpa data cloud yang lebih tua.
                        val local = recordRepo.findBySyncKey(entity.syncKey)
                        if (!ConflictResolver.shouldApplyRemote(local?.updatedAt, entity.updatedAt)) {
                            return@adopt
                        }
                        recordRepo.applyRemote(entity)
                        adopted++
                    },
                    newDocIdOf = { it.syncKey }
                )
            }.onFailure { errors += "Adopsi data lama: ${it.localizedMessage ?: "gagal"}" }
        }

        var recordsPushed = 0
        var recordsApplied = 0
        var recordsPushOk = false
        runCatching { recordsPushed = recordsSync(recordRepo).push().pushed }
            .onSuccess { recordsPushOk = true }
            .onFailure { errors += "Transaksi: ${it.localizedMessage ?: "gagal push"}" }
        runCatching {
            val result = recordsSync(recordRepo).pull(SyncCursors.get(context, COLLECTION_RECORDS))
            SyncCursors.set(context, COLLECTION_RECORDS, result.newestUpdatedAt)
            recordsApplied = result.applied
        }.onFailure { errors += "Transaksi: ${it.localizedMessage ?: "gagal pull"}" }

        var recurringPushed = 0
        var recurringApplied = 0
        runCatching { recurringPushed = recurringSync(recurringRepo).push().pushed }
            .onFailure { errors += "Transaksi berulang: ${it.localizedMessage ?: "gagal push"}" }
        runCatching {
            val result = recurringSync(recurringRepo).pull(SyncCursors.get(context, COLLECTION_RECURRING))
            SyncCursors.set(context, COLLECTION_RECURRING, result.newestUpdatedAt)
            recurringApplied = result.applied
        }.onFailure { errors += "Transaksi berulang: ${it.localizedMessage ?: "gagal pull"}" }

        var categoriesPushed = 0
        var categoriesApplied = 0
        runCatching { categoriesPushed = categoriesSync(categoryRepo).push().pushed }
            .onFailure { errors += "Kategori: ${it.localizedMessage ?: "gagal push"}" }
        runCatching {
            val result = categoriesSync(categoryRepo).pull(SyncCursors.get(context, COLLECTION_CATEGORIES))
            SyncCursors.set(context, COLLECTION_CATEGORIES, result.newestUpdatedAt)
            categoriesApplied = result.applied
        }.onFailure { errors += "Kategori: ${it.localizedMessage ?: "gagal pull"}" }

        var budgetsPushed = 0
        var budgetsApplied = 0
        runCatching { budgetsPushed = budgetsSync(budgetRepo).push().pushed }
            .onFailure { errors += "Anggaran: ${it.localizedMessage ?: "gagal push"}" }
        runCatching {
            val result = budgetsSync(budgetRepo).pull(SyncCursors.get(context, COLLECTION_CATEGORY_BUDGETS))
            SyncCursors.set(context, COLLECTION_CATEGORY_BUDGETS, result.newestUpdatedAt)
            budgetsApplied = result.applied
        }.onFailure { errors += "Anggaran: ${it.localizedMessage ?: "gagal pull"}" }

        var settingsPushed = 0
        var settingsApplied = 0
        runCatching { settingsPushed = settingsSync(settingsRepo).push().pushed }
            .onFailure { errors += "Pengaturan: ${it.localizedMessage ?: "gagal push"}" }
        runCatching {
            val result = settingsSync(settingsRepo).pull(SyncCursors.get(context, COLLECTION_SETTINGS))
            SyncCursors.set(context, COLLECTION_SETTINGS, result.newestUpdatedAt)
            settingsApplied = result.applied
        }.onFailure { errors += "Pengaturan: ${it.localizedMessage ?: "gagal pull"}" }

        // Dokumen legacy baru boleh dihapus setelah versi barunya terbukti ada di
        // cloud. Kalau push gagal, dokumen lama tetap dibiarkan sebagai cadangan.
        if (legacyPending && recordsPushOk) {
            runCatching {
                LegacyAdoption.deleteVerified(legacyRefs, userCollection(COLLECTION_RECORDS))
            }.onFailure { Log.w(TAG, "Bersihkan dokumen legacy gagal: ${it.message}") }
            SyncCursors.markAdopted(context, COLLECTION_RECORDS)
        } else if (legacyPending) {
            Log.w(TAG, "Push transaksi gagal, adopsi legacy dicoba lagi nanti.")
        }

        // Sisa dari versi 6: dokumen "t:legacy:<id>" yang tadinya duplikat dokumen
        // "<id>". Setelah MIGRATION_6_7 kunci lokal kembali ke "<id>", dokumen sisa ini
        // tidak punya pasangan lokal dan akan ditarik balik sebagai duplikat saat pull.
        // Bersihkan sekali saja, dan hanya dokumen yang pasangannya terkonfirmasi ada.
        if (!SyncCursors.isAdopted(context, CLEANUP_RENAMED_FLAG)) {
            runCatching {
                val removedRecords = LegacyAdoption.deleteRenamedDuplicates(
                    collectionRef = userCollection(COLLECTION_RECORDS),
                    prefix = LEGACY_KEY_PREFIX
                ) { key -> recordRepo.findBySyncKey(key) != null }
                val removedRecurring = LegacyAdoption.deleteRenamedDuplicates(
                    collectionRef = userCollection(COLLECTION_RECURRING),
                    prefix = LEGACY_KEY_PREFIX
                ) { key -> recurringRepo.findBySyncKey(key) != null }
                Log.i(TAG, "Pembersihan duplikat: $removedRecords transaksi, $removedRecurring recurring")
            }.onFailure { Log.w(TAG, "Pembersihan dokumen duplikat gagal: ${it.message}") }
            SyncCursors.markAdopted(context, CLEANUP_RENAMED_FLAG)
        }

        Report(
            recordsPushed = recordsPushed,
            recordsApplied = recordsApplied,
            recurringPushed = recurringPushed,
            recurringApplied = recurringApplied,
            categoriesPushed = categoriesPushed,
            categoriesApplied = categoriesApplied,
            budgetsPushed = budgetsPushed,
            budgetsApplied = budgetsApplied,
            settingsPushed = settingsPushed,
            settingsApplied = settingsApplied,
            legacyAdopted = adopted,
            errors = errors
        )
    }

    private fun allSyncs(
        db: AppDatabase,
        recordRepo: FinancialRecordRepository,
        recurringRepo: RecurringTransactionRepository,
        categoryRepo: CategoryRepository,
        budgetRepo: CategoryBudgetRepository,
        settingsRepo: AppSettingsRepository
    ) = listOf(
        recordsSync(recordRepo),
        recurringSync(recurringRepo),
        categoriesSync(categoryRepo),
        budgetsSync(budgetRepo),
        settingsSync(settingsRepo)
    )

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

    private fun categoriesSync(repo: CategoryRepository): CollectionSync<Category> {
        val ref = userCollection(COLLECTION_CATEGORIES)

        return CollectionSync(
            name = COLLECTION_CATEGORIES,
            collectionRef = ref,
            pendingItems = { repo.getPendingSync() },
            findLocal = { repo.findById(it) },
            applyRemote = { repo.applyRemote(it) },
            markSynced = { repo.markSynced(it) },
            toDocument = { category ->
                mapOf(
                    CollectionSync.FIELD_SYNC_KEY to category.syncKey,
                    "name" to category.name,
                    "type" to category.type,
                    CollectionSync.FIELD_UPDATED_AT to category.updatedAt,
                    CollectionSync.FIELD_IS_DELETED to category.isDeleted
                )
            },
            fromDocument = { doc ->
                val updatedAt = (doc.get(CollectionSync.FIELD_UPDATED_AT) as? Number)?.toLong()
                    ?: return@CollectionSync null
                Category(
                    id = doc.id,
                    name = doc.getString("name").orEmpty(),
                    type = doc.getString("type").orEmpty(),
                    isDeleted = doc.getBoolean(CollectionSync.FIELD_IS_DELETED) ?: false,
                    updatedAt = updatedAt,
                    syncKey = doc.getString(CollectionSync.FIELD_SYNC_KEY) ?: doc.id,
                    syncStatus = SyncStatus.SYNCED
                )
            }
        )
    }

    private fun budgetsSync(repo: CategoryBudgetRepository): CollectionSync<CategoryBudget> {
        val ref = userCollection(COLLECTION_CATEGORY_BUDGETS)

        return CollectionSync(
            name = COLLECTION_CATEGORY_BUDGETS,
            collectionRef = ref,
            pendingItems = { repo.getPendingSync() },
            findLocal = { repo.findByCategoryId(it) },
            applyRemote = { repo.applyRemote(it) },
            markSynced = { repo.markSynced(it) },
            toDocument = { budget ->
                mapOf(
                    CollectionSync.FIELD_SYNC_KEY to budget.syncKey,
                    "budgetLimit" to budget.budgetLimit,
                    CollectionSync.FIELD_UPDATED_AT to budget.updatedAt,
                    CollectionSync.FIELD_IS_DELETED to budget.isDeleted
                )
            },
            fromDocument = { doc ->
                val updatedAt = (doc.get(CollectionSync.FIELD_UPDATED_AT) as? Number)?.toLong()
                    ?: return@CollectionSync null
                CategoryBudget(
                    categoryId = doc.id,
                    budgetLimit = (doc.get("budgetLimit") as? Number)?.toLong() ?: 0L,
                    isDeleted = doc.getBoolean(CollectionSync.FIELD_IS_DELETED) ?: false,
                    updatedAt = updatedAt,
                    syncKey = doc.getString(CollectionSync.FIELD_SYNC_KEY) ?: doc.id,
                    syncStatus = SyncStatus.SYNCED
                )
            }
        )
    }

    /** Satu dokumen per key yang ada di allow-list; kunci keamanan tidak pernah ikut. */
    private fun settingsSync(repo: AppSettingsRepository): CollectionSync<AppSetting> {
        val ref = userCollection(COLLECTION_SETTINGS)

        return CollectionSync(
            name = COLLECTION_SETTINGS,
            collectionRef = ref,
            pendingItems = { repo.getPendingSync() },
            findLocal = { repo.find(it) },
            applyRemote = { repo.applyRemote(it) },
            markSynced = { repo.markSynced(it) },
            toDocument = { setting ->
                mapOf(
                    CollectionSync.FIELD_SYNC_KEY to setting.syncKey,
                    "key" to setting.key,
                    "valueText" to setting.valueText,
                    "valueLong" to setting.valueLong,
                    "valueBool" to setting.valueBool,
                    CollectionSync.FIELD_UPDATED_AT to setting.updatedAt
                )
            },
            fromDocument = { doc ->
                val key = doc.getString("key") ?: return@CollectionSync null
                if (!SyncableSettings.isSyncable(key)) return@CollectionSync null
                val updatedAt = (doc.get(CollectionSync.FIELD_UPDATED_AT) as? Number)?.toLong()
                    ?: return@CollectionSync null
                AppSetting(
                    key = key,
                    valueText = doc.getString("valueText"),
                    valueLong = (doc.get("valueLong") as? Number)?.toLong(),
                    valueBool = doc.getBoolean("valueBool"),
                    updatedAt = updatedAt,
                    syncKey = doc.getString(CollectionSync.FIELD_SYNC_KEY) ?: doc.id,
                    syncStatus = SyncStatus.SYNCED
                )
            }
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
        val prefs = trackerPrefs(appContext)
        val syncs = allSyncs(
            db,
            FinancialRecordRepository(db.financialRecordDao()),
            RecurringTransactionRepository(db.recurringTransactionDao()),
            CategoryRepository(db.categoryDao(), prefs),
            CategoryBudgetRepository(db.categoryBudgetDao()),
            AppSettingsRepository(db.appSettingDao())
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