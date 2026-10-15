package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Perilaku impor Excel/CSV: mergeAll tidak menyentuh data lama, replaceAllSoft
 * memindahkan data aktif ke Sampah (bukan hard delete), dan baris hasil impor
 * selalu siap disinkronkan (PENDING + syncKey baru yang unik).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CsvImportReplaceTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: FinancialRecordRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = FinancialRecordRepository.create(db)
    }

    @After
    fun tearDown() = db.close()

    private fun row(description: String, amount: Long = 10_000L) = FinancialRecord(
        description = description,
        amount = amount,
        type = CategoryMigration.TYPE_EXPENSE,
        category = "Makanan",
        date = 1_700_000_000_000L,
        notes = ""
    )

    @Test
    fun merge_tidak_menghapus_data_lama() = runBlocking {
        val existingId = repo.insert(row("Transaksi lama")).toInt()
        val existingKey = db.financialRecordDao().findById(existingId)!!.syncKey

        repo.mergeAll(listOf(row("Baris impor A"), row("Baris impor B")))

        val active = db.financialRecordDao().getAllRecords().first()
        assertEquals("data lama + 2 baris impor harus sama-sama aktif", 3, active.size)
        assertTrue("data lama harus tetap aktif", active.any { it.syncKey == existingKey })
        assertTrue("tidak boleh ada baris yang masuk Sampah", db.financialRecordDao().getDeletedRecords().first().isEmpty())
        assertEquals(3, db.financialRecordDao().getRecordCount())
    }

    @Test
    fun replace_soft_memindah_data_lama_ke_sampah_bukan_hapus_permanen() = runBlocking {
        repo.insert(row("Sudah di Sampah"))
        val sudahDiSampah = db.financialRecordDao().getAllRecords().first()
            .first { it.description == "Sudah di Sampah" }
        repo.delete(sudahDiSampah)

        val existingId = repo.insert(row("Transaksi lama")).toInt()
        val existing = db.financialRecordDao().findById(existingId)!!

        repo.replaceAllSoft(listOf(row("Baris impor")))

        // Baris lama tidak hilang: tetap ada di DB dengan isDeleted = 1 dan syncKey lama.
        val trashed = db.financialRecordDao().findById(existingId)
        assertNotNull("data lama tidak boleh terhapus permanen", trashed)
        assertTrue("data lama harus pindah ke Sampah", trashed!!.isDeleted)
        assertEquals("syncKey lama dipertahankan agar cloud tidak jadi orphan", existing.syncKey, trashed.syncKey)

        // Isi Sampah yang sudah ada sebelumnya juga tidak ikut terhapus.
        val trash = db.financialRecordDao().getDeletedRecords().first()
        assertEquals("Sampah lama + data aktif lama", 2, trash.size)

        val active = db.financialRecordDao().getAllRecords().first()
        assertEquals("hanya baris impor yang aktif", 1, active.size)
        assertEquals("Baris impor", active.single().description)
    }

    @Test
    fun baris_impor_punya_sync_key_baru_unik_dan_status_pending() = runBlocking {
        repo.mergeAll(listOf(row("Baris impor A"), row("Baris impor B")))

        val imported = db.financialRecordDao().getAllRecords().first()
        assertEquals(2, imported.size)
        imported.forEach { record ->
            assertEquals("baris impor harus dikirim ke cloud", SyncStatus.PENDING, record.syncStatus)
            assertTrue("syncKey baru harus dibuat", record.syncKey.startsWith("t:"))
        }
        assertEquals("syncKey tiap baris harus unik", 2, imported.map { it.syncKey }.toSet().size)
    }

    @Test
    fun replace_soft_tetap_membuat_sync_key_baru_yang_unik() = runBlocking {
        repo.insert(row("Transaksi lama"))

        repo.replaceAllSoft(listOf(row("Baris impor A"), row("Baris impor B")))

        val active = db.financialRecordDao().getAllRecords().first()
        assertEquals(2, active.size)
        active.forEach { record ->
            assertEquals(SyncStatus.PENDING, record.syncStatus)
            assertTrue(record.syncKey.startsWith("t:"))
        }
        assertEquals(2, active.map { it.syncKey }.toSet().size)
    }
}
