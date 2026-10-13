package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import net.sqlcipher.database.SupportFactory
import net.sqlcipher.database.SQLiteDatabase
import com.example.util.DatabaseKeyManager

@Database(
    entities = [
        FinancialRecord::class,
        RecurringTransaction::class,
        Category::class,
        CategoryBudget::class,
        AppSetting::class
    ],
    version = 6,
    exportSchema = true
)
@TypeConverters(AppDatabase.Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun financialRecordDao(): FinancialRecordDao
    abstract fun analysisDao(): AnalysisDao
    abstract fun recurringTransactionDao(): RecurringTransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun categoryBudgetDao(): CategoryBudgetDao
    abstract fun appSettingDao(): AppSettingDao

    class Converters {
        @TypeConverter
        fun fromSyncStatus(value: SyncStatus): Int = value.ordinal

        @TypeConverter
        fun toSyncStatus(value: Int): SyncStatus =
            SyncStatus.values().getOrElse(value) { SyncStatus.PENDING }
    }

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Tambahkan kolom 'notes' jika schema v1 belum memilikinya
                try {
                    database.execSQL("ALTER TABLE financial_records ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
                } catch (e: Exception) {
                    // Abaikan jika kolom sudah ada
                }
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `recurring_transactions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `description` TEXT NOT NULL, 
                        `amount` REAL NOT NULL, 
                        `type` TEXT NOT NULL, 
                        `category` TEXT NOT NULL, 
                        `dayOfMonth` INTEGER NOT NULL, 
                        `notes` TEXT NOT NULL DEFAULT '', 
                        `lastRunDate` INTEGER, 
                        `isActive` INTEGER NOT NULL DEFAULT 1
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                try {
                    database.execSQL("ALTER TABLE financial_records ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {
                    // Abaikan jika kolom sudah ada
                }
            }
        }

        // v4 -> v5: kolom `amount` berubah dari REAL (Double) menjadi INTEGER (Long)
        // pada financial_records dan recurring_transactions. Tanpa migrasi ini Room
        // jatuh ke fallbackToDestructiveMigration() dan MENGHAPUS seluruh data.
        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `financial_records_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `description` TEXT NOT NULL,
                        `amount` INTEGER NOT NULL,
                        `type` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `date` INTEGER NOT NULL,
                        `notes` TEXT NOT NULL,
                        `isDeleted` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    INSERT INTO `financial_records_new`
                        (`id`, `description`, `amount`, `type`, `category`, `date`, `notes`, `isDeleted`)
                    SELECT `id`, `description`, CAST(`amount` AS INTEGER), `type`, `category`, `date`, `notes`, `isDeleted`
                    FROM `financial_records`
                    """.trimIndent()
                )
                database.execSQL("DROP TABLE `financial_records`")
                database.execSQL("ALTER TABLE `financial_records_new` RENAME TO `financial_records`")

                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `recurring_transactions_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `description` TEXT NOT NULL,
                        `amount` INTEGER NOT NULL,
                        `type` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `dayOfMonth` INTEGER NOT NULL,
                        `notes` TEXT NOT NULL,
                        `lastRunDate` INTEGER,
                        `isActive` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    INSERT INTO `recurring_transactions_new`
                        (`id`, `description`, `amount`, `type`, `category`, `dayOfMonth`, `notes`, `lastRunDate`, `isActive`)
                    SELECT `id`, `description`, CAST(`amount` AS INTEGER), `type`, `category`, `dayOfMonth`, `notes`, `lastRunDate`, `isActive`
                    FROM `recurring_transactions`
                    """.trimIndent()
                )
                database.execSQL("DROP TABLE `recurring_transactions`")
                database.execSQL("ALTER TABLE `recurring_transactions_new` RENAME TO `recurring_transactions`")
            }
        }

        /**
         * v5 -> v6: metadata sinkronisasi (syncKey unik, updatedAt, syncStatus),
         * soft delete untuk recurring, referensi kategori, dan tabel pendukung
         * (categories, category_budgets, app_settings).
         *
         * PENTING: ada dua lineage DB berbeda yang sama-sama versi 5. Yang di branch
         * main nominalnya sudah INTEGER (Long), sedangkan DB dari percobaan lama masih
         * REAL (Double) dan sudah punya sebagian kolom sync. Karena itu migrasi ini
         * bukan sekadar ALTER: bentuk tabelnya dibaca dulu lewat PRAGMA table_info,
         * lalu tabel dibangun ulang ke bentuk v6 dengan pemetaan kolom terbaik.
         * Nilai nominal dikonversi dengan CAST (1 sen, bukan pembulatan bebas), dan
         * baris lama diberi syncKey unik ('t:legacy:'||id), updatedAt = sekarang,
         * syncStatus = PENDING supaya terkirim sekali saat sinkronisasi pertama.
         */
        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                val now = System.currentTimeMillis()

                rebuildRecords(database, now)
                rebuildRecurring(database, now)
                rebuildCategories(database, now)
                rebuildBudgets(database, now)
                rebuildSettings(database, now)
            }
        }

        /** Nama kolom yang ada di sebuah tabel. */
        private fun columnsOf(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String): Set<String> {
            val names = mutableSetOf<String>()
            db.query("PRAGMA table_info(`$table`)").use { c ->
                val nameIndex = c.getColumnIndex("name")
                if (nameIndex >= 0) {
                    while (c.moveToNext()) names += c.getString(nameIndex)
                }
            }
            return names
        }

        private fun tableExists(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String): Boolean {
            var exists = false
            db.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                arrayOf(table)
            ).use { c -> exists = c.moveToFirst() }
            return exists
        }

        private fun rebuildRecords(db: androidx.sqlite.db.SupportSQLiteDatabase, now: Long) {
            if (!tableExists(db, "financial_records")) return
            val cols = columnsOf(db, "financial_records")

            db.execSQL("DROP TABLE IF EXISTS `financial_records_new`")
            db.execSQL(
                """
                CREATE TABLE `financial_records_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `description` TEXT NOT NULL,
                    `amount` INTEGER NOT NULL,
                    `type` TEXT NOT NULL,
                    `category` TEXT NOT NULL,
                    `date` INTEGER NOT NULL,
                    `notes` TEXT NOT NULL,
                    `isDeleted` INTEGER NOT NULL,
                    `categoryId` TEXT NOT NULL DEFAULT '',
                    `updatedAt` INTEGER NOT NULL DEFAULT 0,
                    `syncKey` TEXT NOT NULL DEFAULT '',
                    `syncStatus` INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )

            val notes = if ("notes" in cols) "COALESCE(`notes`, '')" else "''"
            val isDeleted = if ("isDeleted" in cols) "COALESCE(`isDeleted`, 0)" else "0"
            val categoryId = if ("categoryId" in cols) "COALESCE(`categoryId`, '')" else "''"
            val updatedAt = if ("updatedAt" in cols) "CASE WHEN `updatedAt` > 0 THEN `updatedAt` ELSE $now END" else "$now"
            // syncKey yang sudah ada (dari lineage percobaan lama) dipertahankan agar
            // dokumen cloud-nya tidak orphan; yang kosong diisi dari id baris.
            val syncKey = if ("syncKey" in cols) {
                "CASE WHEN `syncKey` <> '' THEN `syncKey` ELSE 't:legacy:' || `id` END"
            } else {
                "'t:legacy:' || `id`"
            }
            val syncStatus = if ("syncStatus" in cols) "CASE WHEN `syncStatus` IS NULL THEN 1 ELSE `syncStatus` END" else "1"

            db.execSQL(
                """
                INSERT INTO `financial_records_new`
                    (`id`, `description`, `amount`, `type`, `category`, `date`, `notes`, `isDeleted`, `categoryId`, `updatedAt`, `syncKey`, `syncStatus`)
                SELECT `id`,
                       COALESCE(`description`, ''),
                       CAST(`amount` AS INTEGER),
                       COALESCE(`type`, ''),
                       COALESCE(`category`, ''),
                       CAST(`date` AS INTEGER),
                       $notes,
                       $isDeleted,
                       $categoryId,
                       $updatedAt,
                       $syncKey,
                       $syncStatus
                FROM `financial_records`
                """.trimIndent()
            )

            db.execSQL("DROP TABLE `financial_records`")
            db.execSQL("ALTER TABLE `financial_records_new` RENAME TO `financial_records`")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_financial_records_syncKey` ON `financial_records` (`syncKey`)"
            )
        }

        private fun rebuildRecurring(db: androidx.sqlite.db.SupportSQLiteDatabase, now: Long) {
            if (!tableExists(db, "recurring_transactions")) return
            val cols = columnsOf(db, "recurring_transactions")

            db.execSQL("DROP TABLE IF EXISTS `recurring_transactions_new`")
            db.execSQL(
                """
                CREATE TABLE `recurring_transactions_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `description` TEXT NOT NULL,
                    `amount` INTEGER NOT NULL,
                    `type` TEXT NOT NULL,
                    `category` TEXT NOT NULL,
                    `dayOfMonth` INTEGER NOT NULL,
                    `notes` TEXT NOT NULL,
                    `isDeleted` INTEGER NOT NULL DEFAULT 0,
                    `lastRunDate` INTEGER,
                    `isActive` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL DEFAULT 0,
                    `syncKey` TEXT NOT NULL DEFAULT '',
                    `syncStatus` INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )

            val notes = if ("notes" in cols) "COALESCE(`notes`, '')" else "''"
            val isDeleted = if ("isDeleted" in cols) "COALESCE(`isDeleted`, 0)" else "0"
            val lastRunDate = if ("lastRunDate" in cols) "`lastRunDate`" else "NULL"
            val isActive = if ("isActive" in cols) "COALESCE(`isActive`, 1)" else "1"
            val updatedAt = if ("updatedAt" in cols) "CASE WHEN `updatedAt` > 0 THEN `updatedAt` ELSE $now END" else "$now"
            val syncKey = if ("syncKey" in cols) {
                "CASE WHEN `syncKey` <> '' THEN `syncKey` ELSE 'r:legacy:' || `id` END"
            } else {
                "'r:legacy:' || `id`"
            }
            val syncStatus = if ("syncStatus" in cols) "CASE WHEN `syncStatus` IS NULL THEN 1 ELSE `syncStatus` END" else "1"

            db.execSQL(
                """
                INSERT INTO `recurring_transactions_new`
                    (`id`, `description`, `amount`, `type`, `category`, `dayOfMonth`, `notes`, `isDeleted`, `lastRunDate`, `isActive`, `updatedAt`, `syncKey`, `syncStatus`)
                SELECT `id`,
                       COALESCE(`description`, ''),
                       CAST(`amount` AS INTEGER),
                       COALESCE(`type`, ''),
                       COALESCE(`category`, ''),
                       COALESCE(`dayOfMonth`, 1),
                       $notes,
                       $isDeleted,
                       $lastRunDate,
                       $isActive,
                       $updatedAt,
                       $syncKey,
                       $syncStatus
                FROM `recurring_transactions`
                """.trimIndent()
            )

            db.execSQL("DROP TABLE `recurring_transactions`")
            db.execSQL("ALTER TABLE `recurring_transactions_new` RENAME TO `recurring_transactions`")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_recurring_transactions_syncKey` ON `recurring_transactions` (`syncKey`)"
            )
        }

        /**
         * Tabel categories. Kalau sudah ada dari lineage lama, tetap dibangun ulang:
         * baris dengan syncKey kosong akan ditolak Firestore karena documentId kosong.
         */
        private fun rebuildCategories(db: androidx.sqlite.db.SupportSQLiteDatabase, now: Long) {
            if (!tableExists(db, "categories")) {
                db.execSQL(CREATE_CATEGORIES)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_type` ON `categories` (`type`)")
                return
            }
            val cols = columnsOf(db, "categories")

            db.execSQL("DROP TABLE IF EXISTS `categories_new`")
            db.execSQL(CREATE_CATEGORIES_NEW)
            val name = if ("name" in cols) "COALESCE(`name`, '')" else "''"
            val type = if ("type" in cols) "COALESCE(`type`, '')" else "''"
            val isDeleted = if ("isDeleted" in cols) "COALESCE(`isDeleted`, 0)" else "0"
            val updatedAt = if ("updatedAt" in cols) "CASE WHEN `updatedAt` > 0 THEN `updatedAt` ELSE $now END" else "$now"
            val syncKey = if ("syncKey" in cols) "CASE WHEN `syncKey` <> '' THEN `syncKey` ELSE `id` END" else "`id`"
            val syncStatus = if ("syncStatus" in cols) "COALESCE(`syncStatus`, 1)" else "1"

            db.execSQL(
                """
                INSERT INTO `categories_new` (`id`, `name`, `type`, `isDeleted`, `updatedAt`, `syncKey`, `syncStatus`)
                SELECT `id`, $name, $type, $isDeleted, $updatedAt, $syncKey, $syncStatus FROM `categories`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `categories`")
            db.execSQL("ALTER TABLE `categories_new` RENAME TO `categories`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_type` ON `categories` (`type`)")
        }

        private fun rebuildBudgets(db: androidx.sqlite.db.SupportSQLiteDatabase, now: Long) {
            if (!tableExists(db, "category_budgets")) {
                db.execSQL(CREATE_BUDGETS)
                return
            }
            val cols = columnsOf(db, "category_budgets")

            db.execSQL("DROP TABLE IF EXISTS `category_budgets_new`")
            db.execSQL(CREATE_BUDGETS_NEW)
            val limit = if ("budgetLimit" in cols) "CAST(COALESCE(`budgetLimit`, 0) AS INTEGER)" else "0"
            val isDeleted = if ("isDeleted" in cols) "COALESCE(`isDeleted`, 0)" else "0"
            val updatedAt = if ("updatedAt" in cols) "CASE WHEN `updatedAt` > 0 THEN `updatedAt` ELSE $now END" else "$now"
            val syncKey = if ("syncKey" in cols) "CASE WHEN `syncKey` <> '' THEN `syncKey` ELSE `categoryId` END" else "`categoryId`"
            val syncStatus = if ("syncStatus" in cols) "COALESCE(`syncStatus`, 1)" else "1"

            db.execSQL(
                """
                INSERT INTO `category_budgets_new` (`categoryId`, `budgetLimit`, `isDeleted`, `updatedAt`, `syncKey`, `syncStatus`)
                SELECT `categoryId`, $limit, $isDeleted, $updatedAt, $syncKey, $syncStatus FROM `category_budgets`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `category_budgets`")
            db.execSQL("ALTER TABLE `category_budgets_new` RENAME TO `category_budgets`")
        }

        private fun rebuildSettings(db: androidx.sqlite.db.SupportSQLiteDatabase, now: Long) {
            if (!tableExists(db, "app_settings")) {
                db.execSQL(CREATE_SETTINGS)
                return
            }
            val cols = columnsOf(db, "app_settings")

            db.execSQL("DROP TABLE IF EXISTS `app_settings_new`")
            db.execSQL(CREATE_SETTINGS_NEW)
            val valueText = if ("valueText" in cols) "`valueText`" else "NULL"
            // Lineage lama menyimpan angka Long di kolom bernama valueReal.
            val valueLong = when {
                "valueLong" in cols -> "`valueLong`"
                "valueReal" in cols -> "CAST(`valueReal` AS INTEGER)"
                else -> "NULL"
            }
            val valueBool = if ("valueBool" in cols) "`valueBool`" else "NULL"
            val updatedAt = if ("updatedAt" in cols) "CASE WHEN `updatedAt` > 0 THEN `updatedAt` ELSE $now END" else "$now"
            val syncKey = if ("syncKey" in cols) "CASE WHEN `syncKey` <> '' THEN `syncKey` ELSE `key` END" else "`key`"
            val syncStatus = if ("syncStatus" in cols) "COALESCE(`syncStatus`, 1)" else "1"

            db.execSQL(
                """
                INSERT INTO `app_settings_new` (`key`, `valueText`, `valueLong`, `valueBool`, `updatedAt`, `syncKey`, `syncStatus`)
                SELECT `key`, $valueText, $valueLong, $valueBool, $updatedAt, $syncKey, $syncStatus FROM `app_settings`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `app_settings`")
            db.execSQL("ALTER TABLE `app_settings_new` RENAME TO `app_settings`")

            // Baris di luar allow-list dibuang di sini: kolom ini ikut dikirim apa adanya
            // ke Firestore, dan versi lama pernah menyimpan nilai non-angka di sini.
            SyncableSettings.LOCAL_ONLY_KEYS.forEach { key ->
                runCatching { db.execSQL("DELETE FROM `app_settings` WHERE `key` = ?", arrayOf<Any>(key)) }
            }
        }

        private const val CREATE_CATEGORIES =
            """
            CREATE TABLE IF NOT EXISTS `categories` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `isDeleted` INTEGER NOT NULL DEFAULT 0,
                `updatedAt` INTEGER NOT NULL DEFAULT 0,
                `syncKey` TEXT NOT NULL DEFAULT '',
                `syncStatus` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`id`)
            )
            """

        private const val CREATE_CATEGORIES_NEW =
            """
            CREATE TABLE `categories_new` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `isDeleted` INTEGER NOT NULL DEFAULT 0,
                `updatedAt` INTEGER NOT NULL DEFAULT 0,
                `syncKey` TEXT NOT NULL DEFAULT '',
                `syncStatus` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`id`)
            )
            """

        private const val CREATE_BUDGETS =
            """
            CREATE TABLE IF NOT EXISTS `category_budgets` (
                `categoryId` TEXT NOT NULL,
                `budgetLimit` INTEGER NOT NULL DEFAULT 0,
                `isDeleted` INTEGER NOT NULL DEFAULT 0,
                `updatedAt` INTEGER NOT NULL DEFAULT 0,
                `syncKey` TEXT NOT NULL DEFAULT '',
                `syncStatus` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`categoryId`)
            )
            """

        private const val CREATE_BUDGETS_NEW =
            """
            CREATE TABLE `category_budgets_new` (
                `categoryId` TEXT NOT NULL,
                `budgetLimit` INTEGER NOT NULL DEFAULT 0,
                `isDeleted` INTEGER NOT NULL DEFAULT 0,
                `updatedAt` INTEGER NOT NULL DEFAULT 0,
                `syncKey` TEXT NOT NULL DEFAULT '',
                `syncStatus` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`categoryId`)
            )
            """

        private const val CREATE_SETTINGS =
            """
            CREATE TABLE IF NOT EXISTS `app_settings` (
                `key` TEXT NOT NULL,
                `valueText` TEXT,
                `valueLong` INTEGER,
                `valueBool` INTEGER,
                `updatedAt` INTEGER NOT NULL DEFAULT 0,
                `syncKey` TEXT NOT NULL DEFAULT '',
                `syncStatus` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`key`)
            )
            """

        private const val CREATE_SETTINGS_NEW =
            """
            CREATE TABLE `app_settings_new` (
                `key` TEXT NOT NULL,
                `valueText` TEXT,
                `valueLong` INTEGER,
                `valueBool` INTEGER,
                `updatedAt` INTEGER NOT NULL DEFAULT 0,
                `syncKey` TEXT NOT NULL DEFAULT '',
                `syncStatus` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`key`)
            )
            """

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val passphraseBytes = com.example.util.DatabaseKeyManager.getOrCreatePassphrase(context)
                migratePlaintextToEncryptedIfNeeded(context, passphraseBytes)

                val factory = net.sqlcipher.database.SupportFactory(passphraseBytes)

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "financial_tracker_database"
                )
                .openHelperFactory(factory)
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6
                )
                // Sengaja TIDAK memakai fallbackToDestructiveMigration(): kalau ada
                // versi DB tanpa migrasi, aplikasi harus error dengan jelas, bukan
                // diam-diam menghapus seluruh data keuangan pengguna.
                .build()
                INSTANCE = instance
                instance
            }
        }

        private fun migratePlaintextToEncryptedIfNeeded(context: Context, passphraseBytes: ByteArray) {
            val dbName = "financial_tracker_database"
            val dbFile = context.getDatabasePath(dbName)
            if (!dbFile.exists()) return

            val bytes = ByteArray(15)
            var isPlaintext = false
            try {
                java.io.FileInputStream(dbFile).use { fis ->
                    fis.read(bytes)
                }
                val header = String(bytes, Charsets.US_ASCII)
                if (header.startsWith("SQLite format 3")) {
                    isPlaintext = true
                }
            } catch (e: Exception) {
                // Diabaikan jika gagal membaca
            }

            if (!isPlaintext) {
                return
            }

            val tempFile = java.io.File(context.cacheDir, "encrypted_temp.db")
            if (tempFile.exists()) tempFile.delete()

            try {
                net.sqlcipher.database.SQLiteDatabase.loadLibs(context)

                val database = net.sqlcipher.database.SQLiteDatabase.openDatabase(
                    dbFile.absolutePath,
                    "",
                    null,
                    net.sqlcipher.database.SQLiteDatabase.OPEN_READWRITE
                )

                val hexChars = CharArray(passphraseBytes.size * 2)
                val hexDigits = "0123456789abcdef".toCharArray()
                for (i in passphraseBytes.indices) {
                    val v = passphraseBytes[i].toInt() and 0xFF
                    hexChars[i * 2] = hexDigits[v ushr 4]
                    hexChars[i * 2 + 1] = hexDigits[v and 0x0F]
                }
                val passphraseHex = String(hexChars)
                java.util.Arrays.fill(hexChars, '0')

                database.rawExecSQL("ATTACH DATABASE '${tempFile.absolutePath}' AS encrypted KEY x'${passphraseHex}'")
                database.rawExecSQL("SELECT sqlcipher_export('encrypted')")
                database.rawExecSQL("DETACH DATABASE encrypted")
                database.close()

                if (tempFile.exists() && tempFile.length() > 0) {
                    dbFile.delete()
                    val moved = tempFile.renameTo(dbFile)
                    if (!moved) {
                        throw IllegalStateException("Gagal memindahkan file database terenkripsi")
                    }
                }
            } catch (e: Exception) {
                com.example.util.SecureLog.e("AppDatabase", "Gagal memigrasi database menjadi terenkripsi: ${e.message}", e)
            } finally {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }
        }

        fun resetDatabaseInstance() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}
