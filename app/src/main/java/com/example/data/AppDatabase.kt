package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.sqlcipher.database.SupportFactory
import net.sqlcipher.database.SQLiteDatabase
import com.example.util.DatabaseKeyManager

@Database(entities = [FinancialRecord::class, RecurringTransaction::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun financialRecordDao(): FinancialRecordDao
    abstract fun analysisDao(): AnalysisDao
    abstract fun recurringTransactionDao(): RecurringTransactionDao

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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration()
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
                android.util.Log.e("AppDatabase", "Gagal memigrasi database menjadi terenkripsi: ${e.message}", e)
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
