package com.example.data

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Migrasi Room v5 -> v6 (metadata sinkronisasi) di atas skema main yang sudah
 * memakai Long untuk nominal. Data lama wajib utuh: hanya kolom baru yang diisi.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration5To6Test {

    private val dbName = "mig_5_to_6_test"

    @Before
    fun setUp() {
        listOf(dbName, "$dbName-wal", "$dbName-shm").forEach { name ->
            File(ApplicationProvider.getApplicationContext<android.content.Context>().getDatabasePath(name).absolutePath)
                .let { if (it.exists()) it.delete() }
        }
    }

    @After
    fun tearDown() {
        listOf(dbName, "$dbName-wal", "$dbName-shm").forEach { name ->
            File(ApplicationProvider.getApplicationContext<android.content.Context>().getDatabasePath(name).absolutePath)
                .let { if (it.exists()) it.delete() }
        }
    }

    /** Skema persis seperti v5 di branch main: amount INTEGER, tanpa DEFAULT. */
    private fun createV5() {
        val config = SupportSQLiteOpenHelper.Configuration
            .builder(ApplicationProvider.getApplicationContext())
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE `financial_records` (
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
                    db.execSQL(
                        """
                        CREATE TABLE `recurring_transactions` (
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
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()

        FrameworkSQLiteOpenHelperFactory().create(config).use { helper ->
            val db = helper.writableDatabase
            db.execSQL(
                "INSERT INTO financial_records (description, amount, type, category, date, notes, isDeleted) " +
                    "VALUES ('Gaji', 5000000, 'income', 'Gaji', 1700000000000, '', 0)"
            )
            db.execSQL(
                "INSERT INTO financial_records (description, amount, type, category, date, notes, isDeleted) " +
                    "VALUES ('Bakso', 25000, 'expense', 'Makanan', 1700000001000, 'manual', 1)"
            )
            db.execSQL(
                "INSERT INTO recurring_transactions (description, amount, type, category, dayOfMonth, notes, isActive) " +
                    "VALUES ('Internet', 350000, 'expense', 'Tagihan', 5, '', 1)"
            )
        }
    }

    /**
     * Skema lineage percobaan lama: nominal masih REAL (Double), kolom sync sudah ada,
     * dan tabel pendukung sudah dibuat dengan kolom angka REAL.
     * Versi DB-nya sama-sama 5, jadi Room tidak bisa membedakannya dari nomor versi.
     */
    private fun createDivergentV5() {
        val config = SupportSQLiteOpenHelper.Configuration
            .builder(ApplicationProvider.getApplicationContext())
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE `financial_records` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `description` TEXT NOT NULL,
                            `amount` REAL NOT NULL,
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
                    db.execSQL(
                        """
                        CREATE TABLE `recurring_transactions` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `description` TEXT NOT NULL,
                            `amount` REAL NOT NULL,
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
                    db.execSQL(
                        """
                        CREATE TABLE `categories` (
                            `id` TEXT NOT NULL,
                            `name` TEXT NOT NULL,
                            `type` TEXT NOT NULL,
                            `isDeleted` INTEGER NOT NULL DEFAULT 0,
                            `updatedAt` INTEGER NOT NULL DEFAULT 0,
                            `syncKey` TEXT NOT NULL DEFAULT '',
                            `syncStatus` INTEGER NOT NULL DEFAULT 0,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE `category_budgets` (
                            `categoryId` TEXT NOT NULL,
                            `budgetLimit` REAL NOT NULL DEFAULT 0,
                            `isDeleted` INTEGER NOT NULL DEFAULT 0,
                            `updatedAt` INTEGER NOT NULL DEFAULT 0,
                            `syncKey` TEXT NOT NULL DEFAULT '',
                            `syncStatus` INTEGER NOT NULL DEFAULT 0,
                            PRIMARY KEY(`categoryId`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE `app_settings` (
                            `key` TEXT NOT NULL,
                            `valueText` TEXT,
                            `valueReal` REAL,
                            `updatedAt` INTEGER NOT NULL DEFAULT 0,
                            `syncKey` TEXT NOT NULL DEFAULT '',
                            `syncStatus` INTEGER NOT NULL DEFAULT 0,
                            PRIMARY KEY(`key`)
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()

        FrameworkSQLiteOpenHelperFactory().create(config).use { helper ->
            val db = helper.writableDatabase
            db.execSQL(
                "INSERT INTO financial_records (description, amount, type, category, date, notes, isDeleted, categoryId, updatedAt, syncKey, syncStatus) " +
                    "VALUES ('Kopi', 15000.75, 'expense', 'Makanan', 1700000000000, '', 0, 'cat:expense:makanan', 1700000000500, 't:ada', 0)"
            )
            db.execSQL(
                "INSERT INTO financial_records (description, amount, type, category, date, notes, isDeleted, categoryId, updatedAt, syncKey, syncStatus) " +
                    "VALUES ('Pulsa', 25000.0, 'expense', 'Tagihan', 1700000001000, '', 1, '', 0, '', 1)"
            )
            db.execSQL(
                "INSERT INTO recurring_transactions (description, amount, type, category, dayOfMonth, notes, isDeleted, lastRunDate, isActive, updatedAt, syncKey, syncStatus) " +
                    "VALUES ('Listrik', 450000.5, 'expense', 'Tagihan', 10, '', 0, NULL, 1, 0, '', 1)"
            )
            db.execSQL(
                "INSERT INTO categories (id, name, type, isDeleted, updatedAt, syncKey, syncStatus) " +
                    "VALUES ('cat:expense:makanan', 'Makanan', 'expense', 0, 0, '', 0)"
            )
            db.execSQL(
                "INSERT INTO category_budgets (categoryId, budgetLimit, isDeleted, updatedAt, syncKey, syncStatus) " +
                    "VALUES ('cat:expense:makanan', 750000.75, 0, 0, '', 0)"
            )
            db.execSQL(
                "INSERT INTO app_settings (key, valueText, valueReal, updatedAt, syncKey, syncStatus) " +
                    "VALUES ('monthly_budget_limit', NULL, 3000000.0, 0, '', 1)"
            )
            db.execSQL(
                "INSERT INTO app_settings (key, valueText, valueReal, updatedAt, syncKey, syncStatus) " +
                    "VALUES ('saved_pin', '1234', NULL, 0, '', 1)"
            )
        }
    }

    private fun openRoom() = Room.databaseBuilder(
        ApplicationProvider.getApplicationContext(),
        AppDatabase::class.java,
        dbName
    ).addMigrations(
        AppDatabase.MIGRATION_1_2,
        AppDatabase.MIGRATION_2_3,
        AppDatabase.MIGRATION_3_4,
        AppDatabase.MIGRATION_4_5,
        AppDatabase.MIGRATION_5_6
    ).build()

    @Test
    fun data_lama_utuh_dan_baris_baru_terstempel_peming() = runBlocking {
        createV5()
        val db = openRoom()
        try {
            val writable = db.openHelper.writableDatabase

            // Data lama tidak berubah: nominal, catatan, soft delete Sampah.
            writable.query(
                "SELECT description, amount, notes, isDeleted, syncKey, updatedAt, syncStatus " +
                    "FROM financial_records ORDER BY id"
            ).use { c ->
                assertEquals(2, c.count)

                assertTrue(c.moveToFirst())
                assertEquals("Gaji", c.getString(0))
                assertEquals(5_000_000L, c.getLong(1))
                assertEquals(0, c.getInt(3))
                assertEquals("t:legacy:1", c.getString(4))
                assertTrue(c.getLong(5) > 0L)
                assertEquals(SyncStatus.PENDING.ordinal, c.getInt(6))

                assertTrue(c.moveToNext())
                assertEquals("Bakso", c.getString(0))
                assertEquals(25_000L, c.getLong(1))
                assertEquals("manual", c.getString(2))
                assertEquals(1, c.getInt(3))
                assertEquals("t:legacy:2", c.getString(4))
            }

            // syncKey unik per baris.
            writable.query(
                "SELECT syncKey, COUNT(*) c FROM financial_records GROUP BY syncKey HAVING c > 1"
            ).use { assertEquals(0, it.count) }

            // Recurring: soft delete default 0 + metadata terisi.
            writable.query(
                "SELECT description, amount, isDeleted, syncKey, updatedAt, syncStatus FROM recurring_transactions"
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Internet", c.getString(0))
                assertEquals(350_000L, c.getLong(1))
                assertEquals(0, c.getInt(2))
                assertEquals("r:legacy:1", c.getString(3))
                assertTrue(c.getLong(4) > 0L)
                assertEquals(SyncStatus.PENDING.ordinal, c.getInt(5))
            }

            // Tabel baru harus ada.
            listOf("categories", "category_budgets", "app_settings").forEach { table ->
                writable.query(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)
                ).use { assertTrue("tabel $table harus dibuat", it.moveToFirst()) }
            }

            // Query baca tetap benar: Sampah (isDeleted=1) tidak masuk daftar aktif.
            assertEquals(1, db.financialRecordDao().getRecordCount())
            assertNotNull(db.financialRecordDao().findBySyncKey("t:legacy:1"))
            assertEquals(1, db.recurringTransactionDao().getActiveRecurring().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun db_lineage_lama_dipakai_bukan_hanya_lineage_main() = runBlocking {
        createDivergentV5()
        val db = openRoom()
        try {
            val writable = db.openHelper.writableDatabase

            // Nominal REAL -> INTEGER, dan syncKey yang sudah ada tidak ditimpa
            // (kalau ditimpa, dokumen cloud-nya jadi orphan lalu dobel).
            writable.query(
                "SELECT amount, syncKey, syncStatus, updatedAt, categoryId FROM financial_records ORDER BY id"
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(15_000L, c.getLong(0))
                assertEquals("t:ada", c.getString(1))
                assertEquals(SyncStatus.SYNCED.ordinal, c.getInt(2))
                assertEquals(1_700_000_000_500L, c.getLong(3))
                assertEquals("cat:expense:makanan", c.getString(4))

                // Yang belum punya syncKey tetap diisi dari id baris.
                assertTrue(c.moveToNext())
                assertEquals(25_000L, c.getLong(0))
                assertEquals("t:legacy:2", c.getString(1))
                assertEquals(SyncStatus.PENDING.ordinal, c.getInt(2))
            }

            writable.query(
                "SELECT amount, syncKey FROM recurring_transactions"
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(450_000L, c.getLong(0))
                assertEquals("r:legacy:1", c.getString(1))
            }

            // syncKey kategori yang kosong harus diisi, kalau tidak documentId-nya
            // tidak sah dan push ditolak Firestore.
            assertEquals(
                "cat:expense:makanan",
                db.categoryDao().findById("cat:expense:makanan")!!.syncKey
            )
            assertEquals(750_000L, db.categoryBudgetDao().findByCategoryId("cat:expense:makanan")!!.budgetLimit)
            assertEquals(3_000_000L, db.appSettingDao().find(SyncableSettings.MONTHLY_BUDGET_LIMIT)!!.valueLong)

            // Baris di luar allow-list dibuang saat migrasi.
            assertNull(db.appSettingDao().find("saved_pin"))
        } finally {
            db.close()
        }
    }

    @Test
    fun trash_restore_menandai_pending_lagi() = runBlocking {
        createV5()
        val db = openRoom()
        try {
            // Sampah -> aktif harus jadi PENDING supaya ikut tersinkron.
            db.financialRecordDao().restoreRecords(listOf(2), System.currentTimeMillis())

            val restored = db.financialRecordDao().findBySyncKey("t:legacy:2")!!
            assertEquals(false, restored.isDeleted)
            assertEquals(SyncStatus.PENDING, restored.syncStatus)
        } finally {
            db.close()
        }
    }
}