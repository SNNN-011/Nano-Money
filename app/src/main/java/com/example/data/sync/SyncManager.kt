package com.example.data.sync

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.AppDatabase
import com.example.data.AppSetting
import com.example.data.AppSettingsRepository
import com.example.data.Category
import com.example.data.CategoryBudget
import com.example.data.CategoryBudgetRepository
import com.example.data.CategoryMigration
import com.example.data.CategoryRepository
import com.example.data.FinancialRecord
import com.example.data.FinancialRecordRepository
import com.example.data.RecurringTransaction
import com.example.data.RecurringTransactionRepository
import com.example.data.SyncStatus
import com.example.data.SyncableSettings
import com.example.util.SecurePrefsHelper
import com.example.worker.SyncWorker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

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

    /** Penanda impor dokumen preferensi lawas, dipakai sekali seumur device. */
    const val LEGACY_SETTINGS_FLAG = "legacy_settings_v1"

    /** Nama unik pekerjaan sinkronisasi periodik (L6); dipakai juga oleh SyncWorker. */
    const val PERIODIC_SYNC_WORK = "firestore_periodic_sync"

    /** Serialisasi pemeriksaan ganti akun (K5) supaya tidak ada dua wipe/dua push bersamaan. */
    private val accountChangeMutex = Mutex()

    /** Id dokumen yang terakhir terlihat di listener realtime, per koleksi (M4). */
    private val knownDocIds = ConcurrentHashMap<String, Set<String>>()

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
     *
     * Sebelum push, dipastikan dulu tidak terjadi ganti akun (K5): data akun lama
     * tidak boleh terkirim ke uid baru.
     */
    suspend fun pushPending(context: Context) = withContext(Dispatchers.IO) {
        // Toggle default MATI: tanpa izin eksplisit, tidak ada data ke cloud.
        if (!SyncCursors.isSyncEnabled(context)) return@withContext
        if (handleAccountChangeIfNeeded(context)) {
            Log.i(TAG, "Ganti akun terdeteksi: data lokal lama dibersihkan sebelum push.")
        }
        val db = AppDatabase.getDatabase(context)
        val prefs = trackerPrefs(context)
        val syncs = allSyncs(
            context,
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

    /**
     * K5: bila uid Firebase berbeda dari uid terakhir yang tersinkron di perangkat ini,
     * seluruh isi tabel Room (hanya isinya — skema, enkripsi DB, dan PIN tidak disentuh),
     * kursor sync, flag adopsi, serta tombstone dibersihkan lebih dulu; uid baru dicatat
     * sebelum push pertama.
     *
     * Mengembalikan true bila pembersihan benar-benar terjadi. Aman dipanggil berulang
     * dan dari beberapa korut sekaligus (dilindungi mutex).
     */
    suspend fun handleAccountChangeIfNeeded(context: Context): Boolean = withContext(Dispatchers.IO) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return@withContext false
        accountChangeMutex.withLock {
            val storedUid = SyncCursors.getLastUid(context)
            if (storedUid == null) {
                // Belum pernah sync di perangkat ini: adopsi uid sekarang, jangan wipe.
                SyncCursors.setLastUid(context, currentUid)
                return@withLock false
            }
            if (storedUid == currentUid) return@withLock false

            Log.i(TAG, "Akun berganti ($storedUid -> $currentUid); data lokal akun lama dibersihkan.")
            wipeLocalDataForAccountChange(context)
            SyncCursors.resetForAccountChange(context)
            knownDocIds.clear()
            SyncCursors.setLastUid(context, currentUid)
            true
        }
    }

    private suspend fun wipeLocalDataForAccountChange(context: Context) {
        val db = AppDatabase.getDatabase(context)
        db.withTransaction {
            db.financialRecordDao().deleteAllHard()
            db.recurringTransactionDao().deleteAllHard()
            db.categoryDao().deleteAllHard()
            db.categoryBudgetDao().deleteAllHard()
            db.appSettingDao().deleteAllHard()
        }

        // Preferensi lawas (daftar kategori & anggaran) harus ikut dibersihkan.
        // Kalau hanya isi Room yang dibuang sementara CSV-nya tetap ada, migrasi
        // berikutnya akan membangkitkan kembali kategori milik akun lama (dan bila
        // flag migrasinya dibiarkan true, tabel justru kosong permanen).
        val prefs = trackerPrefs(context)
        val editor = prefs.edit()
        editor.remove(CategoryMigration.PREF_INCOME_LIST)
        editor.remove(CategoryMigration.PREF_EXPENSE_LIST)
        editor.remove(CategoryMigration.PREF_MIGRATED_FLAG)
        editor.remove(AppSettingsRepository.PREF_MIGRATED_FLAG)
        editor.remove(SyncableSettings.MONTHLY_BUDGET_LIMIT)
        prefs.all.keys
            .filter { it.startsWith(CategoryMigration.PREF_BUDGET_PREFIX) }
            .forEach { editor.remove(it) }
        editor.commit()
    }

    /**
     * Menandai SEMUA baris lokal PENDING + updatedAt=sekarang, supaya ikut
     * diunggah penuh. Dipakai saat menyalakan toggle dan setelah hapus data cloud.
     */
    suspend fun markAllLocalPending(context: Context) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val now = System.currentTimeMillis()
        db.withTransaction {
            db.financialRecordDao().markAllPendingForRestore(now)
            db.recurringTransactionDao().markAllPendingForRestore(now)
            db.categoryDao().markAllPendingForRestore(now)
            db.categoryBudgetDao().markAllPendingForRestore(now)
            db.appSettingDao().markAllPendingForRestore(now)
        }
        Log.i(TAG, "Semua baris lokal ditandai PENDING (updatedAt=$now).")
    }

    /**
     * Menyalakan sinkronisasi atas persetujuan pengguna: tandai semua data lokal
     * untuk diunggah penuh, lalu nyalakan realtime + pekerjaan periodik.
     * Unggahan sebenarnya dijalankan lewat syncAll oleh pemanggil.
     */
    suspend fun enableSync(context: Context) {
        SyncCursors.setSyncEnabled(context, true)
        markAllLocalPending(context)
        startRealtime(context)
    }

    /**
     * Mematikan sinkronisasi: listener realtime dilepas dan pekerjaan periodik
     * dibatalkan. Data di perangkat TIDAK disentuh sama sekali.
     */
    fun disableSync(context: Context) {
        SyncCursors.setSyncEnabled(context, false)
        stopRealtime()
        runCatching {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(PERIODIC_SYNC_WORK)
        }.onFailure { Log.w(TAG, "Gagal membatalkan sync periodik: ${it.message}") }
    }

    /**
     * Menghapus SELURUH dokumen cloud milik akun yang sedang login (5 koleksi).
     *
     * Data di perangkat tidak disentuh: semua baris lokal ditandai PENDING dan
     * kursor/tombstone direset, jadi kalau pengguna menyalakan sinkronisasi lagi,
     * isi perangkat diunggah ulang penuh — bukan menarik sisa data cloud.
     * Sinkronisasi dimatikan lebih dulu supaya listener tidak ikut mencatat
     * tombstone saat dokumen dihapus massal, dan agar dokumen tidak langsung
     * terunggah ulang oleh pekerja periodik berikutnya.
     */
    suspend fun deleteAllCloudData(context: Context): Report = withContext(Dispatchers.IO) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
            ?: return@withContext Report(errors = listOf("Belum terhubung akun Google."))

        disableSync(context)

        val errors = mutableListOf<String>()
        val collections = listOf(
            COLLECTION_RECORDS,
            COLLECTION_RECURRING,
            COLLECTION_CATEGORIES,
            COLLECTION_CATEGORY_BUDGETS,
            COLLECTION_SETTINGS
        )
        val db = FirebaseFirestore.getInstance()
        var deleted = 0
        for (name in collections) {
            runCatching {
                val documents = userCollection(name).get().await().documents
                documents.chunked(CollectionSync.BATCH_LIMIT).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { batch.delete(it.reference) }
                    withTimeout(CollectionSync.OP_TIMEOUT_MS) { batch.commit().await() }
                }
                deleted += documents.size
                Log.i(TAG, "Hapus cloud koleksi $name: ${documents.size} dokumen.")
            }.onFailure {
                Log.w(TAG, "Gagal menghapus koleksi $name: ${it.message}")
                errors += "$name: ${it.localizedMessage ?: it.message}"
            }
        }

        // Data perangkat tetap ada; siapkan unggah ulang penuh bila dinyalakan lagi.
        markAllLocalPending(context)
        SyncCursors.resetCursorsAndTombstones(context)

        Log.i(TAG, "Hapus data cloud selesai: $deleted dokumen terhapus (uid=$uid).")
        Report(errors = errors)
    }

    /** Sinkron penuh: push + pull tiap koleksi, plus sekali saja adopsi dokumen lama. */
    suspend fun syncAll(context: Context): Report = withContext(Dispatchers.IO) {
        if (!SyncCursors.isSyncEnabled(context)) {
            return@withContext Report(errors = listOf("Sinkronisasi cloud sedang dinonaktifkan."))
        }
        if (FirebaseAuth.getInstance().currentUser == null) {
            return@withContext Report(errors = listOf("Belum terhubung akun Google."))
        }
        if (handleAccountChangeIfNeeded(context)) {
            Log.i(TAG, "Ganti akun terdeteksi: data lokal lama dibersihkan sebelum sync.")
        }

        val db = AppDatabase.getDatabase(context)
        val prefs = trackerPrefs(context)
        val recordRepo = FinancialRecordRepository(db.financialRecordDao())
        val recurringRepo = RecurringTransactionRepository(db.recurringTransactionDao())
        val categoryRepo = CategoryRepository(db.categoryDao(), prefs)
        val budgetRepo = CategoryBudgetRepository(db.categoryBudgetDao())
        val settingsRepo = AppSettingsRepository(db.appSettingDao())
        val errors = mutableListOf<String>()

        // --- Bersihkan sisa duplikat versi 6 (HARUS sebelum pull) ---
        // MIGRATION_6_7 sudah mengembalikan kunci lokal ke "<id>", jadi dokumen
        // "t:legacy:<id>" tidak punya pasangan lokal lagi. Kalau dibiarkan sampai
        // pull, dokumen itu akan ditarik dan membuat baris duplikat yang tidak ikut
        // terhapus. Pengaman: hanya dihapus kalau baris lokal dengan kunci "<id>"
        // benar-benar ada, jadi tidak mungkin menghapus satu-satunya salinan.
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
                        // ada di lokal. Edit lokal yang belum terkirim (PENDING) tidak
                        // boleh tertimpa data cloud; selain itu aturan konflik tetap
                        // dipakai supaya versi lokal yang lebih baru tidak kalah.
                        val local = recordRepo.findBySyncKey(entity.syncKey)
                        if (local?.syncStatus == SyncStatus.PENDING) return@adopt
                        if (!ConflictResolver.shouldApplyRemote(local?.updatedAt, entity.updatedAt)) {
                            return@adopt
                        }
                        // Kalau validasi menolak, dokumen legacy jangan dihitung
                        // teradopsi supaya tidak ikut terhapus dari cloud.
                        if (!recordRepo.applyRemote(entity)) return@adopt
                        adopted++
                    },
                    newDocIdOf = { it.syncKey }
                )
            }.onFailure { errors += "Adopsi data lama: ${it.localizedMessage ?: "gagal"}" }
        }

        // H6: dokumen recurring_transactions lama (tanpa updatedAt/syncKey) juga harus
        // diadopsi; tanpa ini data berulang hanya tersimpan di cloud dan tidak pernah
        // terbawa pull berbasis kursor.
        val legacyRecurringRefs = mutableListOf<LegacyAdoption.Adopted>()
        var legacyRecurringPending = !SyncCursors.isAdopted(context, COLLECTION_RECURRING)
        if (legacyRecurringPending) {
            runCatching {
                legacyRecurringRefs += LegacyAdoption.adopt(
                    collectionRef = userCollection(COLLECTION_RECURRING),
                    fromLegacyDocument = ::mapLegacyRecurring,
                    onAdopt = { entity ->
                        val local = recurringRepo.findBySyncKey(entity.syncKey)
                        if (local?.syncStatus == SyncStatus.PENDING) return@adopt
                        if (!ConflictResolver.shouldApplyRemote(local?.updatedAt, entity.updatedAt)) {
                            return@adopt
                        }
                        if (!recurringRepo.applyRemote(entity)) return@adopt
                        adopted++
                    },
                    newDocIdOf = { it.syncKey }
                )
            }.onFailure { errors += "Adopsi data berulang lama: ${it.localizedMessage ?: "gagal"}" }
        }

        var recordsPushed = 0
        var recordsApplied = 0
        var recordsPushOk = false
        runCatching { recordsPushed = recordsSync(context, recordRepo).push().pushed }
            .onSuccess { recordsPushOk = true }
            .onFailure { errors += "Transaksi: ${it.localizedMessage ?: "gagal push"}" }
        runCatching {
            val result = recordsSync(context, recordRepo).pull(SyncCursors.get(context, COLLECTION_RECORDS))
            SyncCursors.set(context, COLLECTION_RECORDS, result.newestUpdatedAt)
            recordsApplied = result.applied
        }.onFailure { errors += "Transaksi: ${it.localizedMessage ?: "gagal pull"}" }

        var recurringPushed = 0
        var recurringApplied = 0
        var recurringPushOk = false
        runCatching { recurringPushed = recurringSync(context, recurringRepo).push().pushed }
            .onSuccess { recurringPushOk = true }
            .onFailure { errors += "Transaksi berulang: ${it.localizedMessage ?: "gagal push"}" }
        runCatching {
            val result = recurringSync(context, recurringRepo).pull(SyncCursors.get(context, COLLECTION_RECURRING))
            SyncCursors.set(context, COLLECTION_RECURRING, result.newestUpdatedAt)
            recurringApplied = result.applied
        }.onFailure { errors += "Transaksi berulang: ${it.localizedMessage ?: "gagal pull"}" }

        // --- Impor sekali dari dokumen preferensi lawas ---
        // Dilakukan sebelum push kategori/anggaran supaya hasil impor ikut terkirim.
        // Dokumen lawas sendiri baru dihapus setelah push-nya benar-benar berhasil.
        var legacySettingsDoc: com.google.firebase.firestore.DocumentReference? = null
        if (!SyncCursors.isAdopted(context, LEGACY_SETTINGS_FLAG)) {
            runCatching {
                legacySettingsDoc = importLegacySettings(context, db, categoryRepo, budgetRepo, settingsRepo)
            }.onFailure { errors += "Impor pengaturan lama: ${it.localizedMessage ?: "gagal"}" }
        }

        var categoriesPushed = 0
        var categoriesApplied = 0
        var categoriesPushOk = false
        runCatching { categoriesPushed = categoriesSync(context, categoryRepo).push().pushed }
            .onSuccess { categoriesPushOk = true }
            .onFailure { errors += "Kategori: ${it.localizedMessage ?: "gagal push"}" }
        runCatching {
            val result = categoriesSync(context, categoryRepo).pull(SyncCursors.get(context, COLLECTION_CATEGORIES))
            SyncCursors.set(context, COLLECTION_CATEGORIES, result.newestUpdatedAt)
            categoriesApplied = result.applied
        }.onFailure { errors += "Kategori: ${it.localizedMessage ?: "gagal pull"}" }

        var budgetsPushed = 0
        var budgetsApplied = 0
        var budgetsPushOk = false
        runCatching { budgetsPushed = budgetsSync(context, budgetRepo).push().pushed }
            .onSuccess { budgetsPushOk = true }
            .onFailure { errors += "Anggaran: ${it.localizedMessage ?: "gagal push"}" }
        runCatching {
            val result = budgetsSync(context, budgetRepo).pull(SyncCursors.get(context, COLLECTION_CATEGORY_BUDGETS))
            SyncCursors.set(context, COLLECTION_CATEGORY_BUDGETS, result.newestUpdatedAt)
            budgetsApplied = result.applied
        }.onFailure { errors += "Anggaran: ${it.localizedMessage ?: "gagal pull"}" }

        var settingsPushed = 0
        var settingsApplied = 0
        runCatching { settingsPushed = settingsSync(context, settingsRepo).push().pushed }
            .onFailure { errors += "Pengaturan: ${it.localizedMessage ?: "gagal push"}" }
        runCatching {
            val result = settingsSync(context, settingsRepo).pull(SyncCursors.get(context, COLLECTION_SETTINGS))
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

        if (legacyRecurringPending && recurringPushOk) {
            runCatching {
                LegacyAdoption.deleteVerified(legacyRecurringRefs, userCollection(COLLECTION_RECURRING))
            }.onFailure { Log.w(TAG, "Bersihkan dokumen legacy recurring gagal: ${it.message}") }
            SyncCursors.markAdopted(context, COLLECTION_RECURRING)
        } else if (legacyRecurringPending) {
            Log.w(TAG, "Push transaksi berulang gagal, adopsi legacy recurring dicoba lagi nanti.")
        }

        // Dokumen preferensi lawas dihapus setelah isi nyatanya sudah ada di Room dan
        // sudah terkonfirmasi tersimpan di cloud. Kalau push kategori/anggaran gagal,
        // dokumen dibiarkan supaya bisa dicoba lagi pada sync berikutnya.
        val legacyDoc = legacySettingsDoc
        if (legacyDoc != null) {
            if (categoriesPushOk && budgetsPushOk) {
                runCatching { withTimeout(CollectionSync.OP_TIMEOUT_MS) { legacyDoc.delete().await() } }
                    .onSuccess { SyncCursors.markAdopted(context, LEGACY_SETTINGS_FLAG) }
                    .onFailure { Log.w(TAG, "Hapus dokumen preferensi lama gagal: ${it.message}") }
            } else {
                Log.w(TAG, "Push kategori/anggaran gagal, dokumen preferensi lama belum dihapus.")
            }
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
        context: Context,
        db: AppDatabase,
        recordRepo: FinancialRecordRepository,
        recurringRepo: RecurringTransactionRepository,
        categoryRepo: CategoryRepository,
        budgetRepo: CategoryBudgetRepository,
        settingsRepo: AppSettingsRepository
    ) = listOf(
        recordsSync(context, recordRepo),
        recurringSync(context, recurringRepo),
        categoriesSync(context, categoryRepo),
        budgetsSync(context, budgetRepo),
        settingsSync(context, settingsRepo)
    )

    /** M4: id dokumen yang bertombstone dibaca sekali per operasi, bukan per baris. */
    private fun tombstoneFilter(context: Context, collection: String): (String) -> Boolean {
        val tombstones = SyncCursors.getTombstones(context, collection)
        return { id -> id in tombstones }
    }

    private fun userCollection(name: String) =
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(FirebaseAuth.getInstance().currentUser?.uid.orEmpty())
            .collection(name)

    private fun recordsSync(context: Context, repo: FinancialRecordRepository): CollectionSync<FinancialRecord> {
        val ref = userCollection(COLLECTION_RECORDS)

        return CollectionSync(
            name = COLLECTION_RECORDS,
            collectionRef = ref,
            pendingItems = { repo.getPendingSync() },
            findLocal = { repo.findBySyncKey(it) },
            applyRemote = { repo.applyRemote(it) },
            markSynced = { items -> repo.markSyncedIfUnchanged(items.map { it.syncKey to it.updatedAt }) },
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
                    // H12: waktu server, bukan jam perangkat.
                    CollectionSync.FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
                    CollectionSync.FIELD_IS_DELETED to record.isDeleted
                )
            },
            fromDocument = ::mapRemoteRecord,
            isTombstoned = tombstoneFilter(context, COLLECTION_RECORDS)
        )
    }

    private fun recurringSync(context: Context, repo: RecurringTransactionRepository): CollectionSync<RecurringTransaction> {
        val ref = userCollection(COLLECTION_RECURRING)

        return CollectionSync(
            name = COLLECTION_RECURRING,
            collectionRef = ref,
            pendingItems = { repo.getPendingSync() },
            findLocal = { repo.findBySyncKey(it) },
            applyRemote = { repo.applyRemote(it) },
            markSynced = { items -> repo.markSyncedIfUnchanged(items.map { it.syncKey to it.updatedAt }) },
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
                    CollectionSync.FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
                    CollectionSync.FIELD_IS_DELETED to item.isDeleted
                )
            },
            fromDocument = ::mapRemoteRecurring,
            isTombstoned = tombstoneFilter(context, COLLECTION_RECURRING)
        )
    }

    private fun categoriesSync(context: Context, repo: CategoryRepository): CollectionSync<Category> {
        val ref = userCollection(COLLECTION_CATEGORIES)

        return CollectionSync(
            name = COLLECTION_CATEGORIES,
            collectionRef = ref,
            pendingItems = { repo.getPendingSync() },
            findLocal = { repo.findById(it) },
            applyRemote = { repo.applyRemote(it) },
            markSynced = { items -> repo.markSyncedIfUnchanged(items.map { it.syncKey to it.updatedAt }) },
            toDocument = { category ->
                mapOf(
                    CollectionSync.FIELD_SYNC_KEY to category.syncKey,
                    "name" to category.name,
                    "type" to category.type,
                    CollectionSync.FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
                    CollectionSync.FIELD_IS_DELETED to category.isDeleted
                )
            },
            fromDocument = { doc ->
                val updatedAt = readUpdatedAt(doc) ?: return@CollectionSync null
                Category(
                    id = doc.id,
                    name = doc.getString("name").orEmpty(),
                    type = doc.getString("type").orEmpty(),
                    isDeleted = doc.getBoolean(CollectionSync.FIELD_IS_DELETED) ?: false,
                    updatedAt = updatedAt,
                    syncKey = readSyncKey(doc),
                    syncStatus = SyncStatus.SYNCED
                )
            },
            isTombstoned = tombstoneFilter(context, COLLECTION_CATEGORIES)
        )
    }

    private fun budgetsSync(context: Context, repo: CategoryBudgetRepository): CollectionSync<CategoryBudget> {
        val ref = userCollection(COLLECTION_CATEGORY_BUDGETS)

        return CollectionSync(
            name = COLLECTION_CATEGORY_BUDGETS,
            collectionRef = ref,
            pendingItems = { repo.getPendingSync() },
            findLocal = { repo.findByCategoryId(it) },
            applyRemote = { repo.applyRemote(it) },
            markSynced = { items -> repo.markSyncedIfUnchanged(items.map { it.syncKey to it.updatedAt }) },
            toDocument = { budget ->
                mapOf(
                    CollectionSync.FIELD_SYNC_KEY to budget.syncKey,
                    "budgetLimit" to budget.budgetLimit,
                    CollectionSync.FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
                    CollectionSync.FIELD_IS_DELETED to budget.isDeleted
                )
            },
            fromDocument = { doc ->
                val updatedAt = readUpdatedAt(doc) ?: return@CollectionSync null
                CategoryBudget(
                    categoryId = doc.id,
                    budgetLimit = (doc.get("budgetLimit") as? Number)?.toLong() ?: 0L,
                    isDeleted = doc.getBoolean(CollectionSync.FIELD_IS_DELETED) ?: false,
                    updatedAt = updatedAt,
                    syncKey = readSyncKey(doc),
                    syncStatus = SyncStatus.SYNCED
                )
            },
            isTombstoned = tombstoneFilter(context, COLLECTION_CATEGORY_BUDGETS)
        )
    }

    /** Satu dokumen per key yang ada di allow-list; kunci keamanan tidak pernah ikut. */
    private fun settingsSync(context: Context, repo: AppSettingsRepository): CollectionSync<AppSetting> {
        val ref = userCollection(COLLECTION_SETTINGS)

        return CollectionSync(
            name = COLLECTION_SETTINGS,
            collectionRef = ref,
            pendingItems = { repo.getPendingSync() },
            findLocal = { repo.find(it) },
            applyRemote = { repo.applyRemote(it) },
            markSynced = { items -> repo.markSyncedIfUnchanged(items.map { it.syncKey to it.updatedAt }) },
            toDocument = { setting ->
                mapOf(
                    CollectionSync.FIELD_SYNC_KEY to setting.syncKey,
                    "key" to setting.key,
                    "valueText" to setting.valueText,
                    "valueLong" to setting.valueLong,
                    "valueBool" to setting.valueBool,
                    CollectionSync.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                )
            },
            fromDocument = { doc ->
                val key = doc.getString("key") ?: return@CollectionSync null
                if (!SyncableSettings.isSyncable(key)) return@CollectionSync null
                val updatedAt = readUpdatedAt(doc) ?: return@CollectionSync null
                AppSetting(
                    key = key,
                    valueText = doc.getString("valueText"),
                    valueLong = (doc.get("valueLong") as? Number)?.toLong(),
                    valueBool = doc.getBoolean("valueBool"),
                    updatedAt = updatedAt,
                    syncKey = readSyncKey(doc),
                    syncStatus = SyncStatus.SYNCED
                )
            },
            isTombstoned = tombstoneFilter(context, COLLECTION_SETTINGS)
        )
    }

    /**
     * H12: updatedAt kini ditulis serverTimestamp (Timestamp), tapi dokumen lama masih
     * berisi angka epoch ms. Keduanya harus terbaca.
     */
    private fun readUpdatedAt(doc: DocumentSnapshot): Long? =
        doc.getTimestamp(CollectionSync.FIELD_UPDATED_AT)?.toDate()?.time
            ?: (doc.get(CollectionSync.FIELD_UPDATED_AT) as? Number)?.toLong()

    /** H8: syncKey kosong di dokumen dianggap tidak ada; id dokumen jadi fallback. */
    private fun readSyncKey(doc: DocumentSnapshot): String =
        doc.getString(CollectionSync.FIELD_SYNC_KEY)?.takeIf { it.isNotBlank() } ?: doc.id

    private fun mapRemoteRecord(doc: DocumentSnapshot): FinancialRecord? {
        val updatedAt = readUpdatedAt(doc) ?: return null
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
            syncKey = readSyncKey(doc),
            syncStatus = SyncStatus.SYNCED
        )
    }

    private fun mapRemoteRecurring(doc: DocumentSnapshot): RecurringTransaction? {
        val updatedAt = readUpdatedAt(doc) ?: return null
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
            syncKey = readSyncKey(doc),
            syncStatus = SyncStatus.SYNCED
        )
    }

    /**
     * Dokumen legacy milik transaksi berulang (H6): tanpa updatedAt/syncKey.
     * updatedAt memakai waktu tulis server dari field updatedAt (serverTimestamp;
     * SDK Firestore 26.3.0 tidak lagi menyediakan metadata updateTime) supaya edit
     * lokal yang lebih baru tetap menang; waktu adopsi hanya dipakai sebagai
     * cadangan bila dokumen belum punya updatedAt.
     */
    private fun mapLegacyRecurring(doc: DocumentSnapshot): RecurringTransaction? {
        val amount = (doc.get("amount") as? Number)?.toLong() ?: return null
        if (amount <= 0L) return null
        return RecurringTransaction(
            id = 0,
            description = doc.getString("description").orEmpty(),
            amount = amount,
            type = doc.getString("type").orEmpty(),
            category = doc.getString("category").orEmpty(),
            dayOfMonth = (doc.get("dayOfMonth") as? Number)?.toInt() ?: 1,
            notes = doc.getString("notes").orEmpty(),
            isDeleted = doc.getBoolean(CollectionSync.FIELD_IS_DELETED) ?: false,
            lastRunDate = (doc.get("lastRunDate") as? Number)?.toLong(),
            isActive = doc.getBoolean("isActive") ?: true,
            updatedAt = readUpdatedAt(doc) ?: System.currentTimeMillis(),
            syncKey = LegacyAdoption.legacySyncKey(doc.id),
            syncStatus = SyncStatus.PENDING
        )
    }

    /**
     * Membaca dokumen `settings/financial_tracker_prefs` versi lama lalu memindahkan
     * daftar kategori, anggaran, dan anggaran bulanan (M5) ke tabel Room.
     *
     * Hanya field yang diizinkan yang dibaca; kunci keamanan yang kebetulan ada di
     * dokumen itu tidak pernah disentuh. Mengembalikan referensi dokumen itu supaya
     * pemanggil menghapusnya nanti, setelah push-nya terkonfirmasi berhasil.
     *
     * L7: bila dokumen tidak ada (atau kosong dan langsung dihapus), flag impor
     * ditulis di sini supaya tidak ada pembacaan Firestore ekstra di tiap syncAll.
     */
    private suspend fun importLegacySettings(
        context: Context,
        db: AppDatabase,
        categoryRepo: CategoryRepository,
        budgetRepo: CategoryBudgetRepository,
        settingsRepo: AppSettingsRepository
    ): com.google.firebase.firestore.DocumentReference? {
        val docRef = userCollection(COLLECTION_SETTINGS).document(LegacySettingsImport.DOC_ID)
        val snapshot = withTimeout(CollectionSync.OP_TIMEOUT_MS) { docRef.get().await() }
        if (!snapshot.exists()) {
            SyncCursors.markAdopted(context, LEGACY_SETTINGS_FLAG)
            return null
        }

        val imported = LegacySettingsImport.parse(
            docData = LegacySettingsImport.onlyAllowedFields(snapshot.data.orEmpty()),
            existingCategoryIds = db.categoryDao().getAllIds().toSet(),
            now = System.currentTimeMillis()
        )

        if (imported.categories.isNotEmpty()) db.categoryDao().upsertAll(imported.categories)
        if (imported.budgets.isNotEmpty()) db.categoryBudgetDao().upsertAll(imported.budgets)

        // M5: anggaran bulanan ikut diselamatkan sebelum dokumen lawas dihapus.
        // Nilai lokal yang sudah ada tidak boleh ditimpa data lama.
        val monthlyBudget = imported.monthlyBudgetLimit
        if (monthlyBudget != null &&
            settingsRepo.find(SyncableSettings.MONTHLY_BUDGET_LIMIT) == null
        ) {
            settingsRepo.setLong(SyncableSettings.MONTHLY_BUDGET_LIMIT, monthlyBudget)
        }

        Log.i(
            TAG,
            "Impor pengaturan lama: ${imported.categories.size} kategori, " +
                "${imported.budgets.size} anggaran, ${imported.orphanBudgets.size} anggaran yatim"
        )

        // Tidak ada yang perlu dipindahkan: dokumennya tidak berisi data yang kita pakai.
        if (imported.categories.isEmpty() && imported.budgets.isEmpty()) {
            withTimeout(CollectionSync.OP_TIMEOUT_MS) { docRef.delete().await() }
            SyncCursors.markAdopted(context, LEGACY_SETTINGS_FLAG)
            return null
        }
        return docRef
    }

    /**
     * Dokumen legacy (tanpa updatedAt/syncKey) diubah jadi entitas lokal.
     *
     * H6: updatedAt memakai waktu tulis server dari field updatedAt (serverTimestamp;
     * SDK Firestore 26.3.0 tidak lagi menyediakan metadata updateTime), bukan waktu
     * adopsi. Dengan begitu edit lokal yang benar-benar lebih baru tidak kalah dari
     * dokumen cloud yang tua, dan waktu adopsi hanya dipakai sebagai cadangan.
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
            updatedAt = readUpdatedAt(doc) ?: System.currentTimeMillis(),
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
     *
     * K5: pemeriksaan ganti akun dijalankan lebih dulu (di background), supaya
     * listener tidak pernah menempel pada data milik uid yang salah.
     * L6: pekerjaan sinkronisasi periodik juga dinyalakan dari sini, bukan dari Activity.
     */
    fun startRealtime(context: Context) {
        if (!SyncCursors.isSyncEnabled(context)) return
        if (FirebaseAuth.getInstance().currentUser == null) return
        if (!realtimeOn.compareAndSet(false, true)) return

        val appContext = context.applicationContext
        realtimeScope.launch(Dispatchers.IO) {
            val switched = runCatching { handleAccountChangeIfNeeded(appContext) }
                .onFailure { Log.w(TAG, "Pemeriksaan ganti akun gagal: ${it.message}") }
                .getOrDefault(false)
            if (switched) Log.i(TAG, "Ganti akun terdeteksi: data lokal lama dibersihkan.")
            if (FirebaseAuth.getInstance().currentUser == null) {
                realtimeOn.set(false)
                return@launch
            }
            attachRealtimeListeners(appContext)
            enqueuePeriodicSync(appContext)
        }
    }

    private fun attachRealtimeListeners(appContext: Context) {
        val db = AppDatabase.getDatabase(appContext)
        val prefs = trackerPrefs(appContext)
        val syncs = allSyncs(
            appContext,
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
                // M4: id yang hilang dari snapshot berarti dokumennya dihapus fisik
                // di cloud; dicatat sebagai tombstone supaya tidak dikirim ulang.
                detectRemoteDeletions(appContext, sync.name, documents)
                realtimeScope.launch(Dispatchers.IO) {
                    runCatching { sync.applyRemoteDocuments(documents) }
                        .onFailure { Log.w(TAG, "Realtime ${sync.name} gagal apply: ${it.message}") }
                }
            }
            if (registration != null) registrations.add(registration)
        }
    }

    /**
     * Membandingkan id dokumen antar snapshot listener. Snapshot pertama hanya
     * menyemai pembanding, jadi penghapusan yang terjadi sebelum listener menempel
     * tidak terdeteksi — batas yang diketahui dari pendekatan tombstone lokal ini.
     */
    private fun detectRemoteDeletions(context: Context, collection: String, documents: List<DocumentSnapshot>) {
        val current = documents.mapTo(mutableSetOf()) { it.id }
        val previous = knownDocIds.put(collection, current) ?: return
        val removed = previous.filterTo(mutableSetOf()) { it !in current }
        if (removed.isNotEmpty()) {
            Log.i(TAG, "Terdeteksi ${removed.size} dokumen $collection terhapus di cloud, dibuat tombstone.")
            SyncCursors.addTombstones(context, collection, removed)
        }
    }

    /** L6: sinkronisasi periodik; KEEP supaya jadwal yang sudah ada tidak di-reset. */
    private fun enqueuePeriodicSync(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(60, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** Melepas semua listener (dipakai saat logout). */
    fun stopRealtime() {
        registrations.forEach { runCatching { it.remove() } }
        registrations.clear()
        knownDocIds.clear()
        realtimeOn.set(false)
    }
}