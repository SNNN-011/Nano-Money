package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Aturan mutasi transaksi: syncKey tidak boleh berubah saat edit (kalau berubah,
 * dokumen cloud lama jadi orphan lalu muncul balik sebagai duplikat), dan
 * categoryId harus selalu terisi.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FinancialRecordRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: FinancialRecordRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = FinancialRecordRepository.create(db)
        runBlocking {
            db.categoryDao().upsertAll(
                listOf(
                    Category(id = "cat:expense:makanan", name = "Makanan", type = CategoryMigration.TYPE_EXPENSE),
                    Category(id = "cat:expense:kuliner", name = "Kuliner", type = CategoryMigration.TYPE_EXPENSE)
                )
            )
        }
    }

    @After
    fun tearDown() = db.close()

    private fun draft(category: String) = FinancialRecord(
        description = "Bakso",
        amount = 25_000L,
        type = CategoryMigration.TYPE_EXPENSE,
        category = category,
        date = 1_700_000_000_000L,
        notes = ""
    )

    @Test
    fun insert_mengisi_sync_key_dan_category_id() = runBlocking {
        val id = repo.insert(draft("Makanan")).toInt()

        val saved = db.financialRecordDao().findById(id)!!
        assertTrue(saved.syncKey.startsWith("t:"))
        assertEquals("cat:expense:makanan", saved.categoryId)
        assertEquals(SyncStatus.PENDING, saved.syncStatus)
    }

    @Test
    fun edit_mempertahankan_sync_key_lama() = runBlocking {
        val id = repo.insert(draft("Makanan")).toInt()
        val original = db.financialRecordDao().findById(id)!!

        // Form hanya mengirim isi baru: tanpa id, tanpa syncKey.
        repo.update(FinancialRecord(id = id, description = "Bakso Spesial", amount = 30_000L, type = "expense", category = "Makanan", date = 1_700_000_000_000L, notes = ""))

        val updated = db.financialRecordDao().findById(id)!!
        assertEquals("syncKey harus tetap, kalau tidak dokumen cloud jadi orphan", original.syncKey, updated.syncKey)
        assertEquals("Bakso Spesial", updated.description)
        assertEquals(30_000L, updated.amount)
        assertEquals(SyncStatus.PENDING, updated.syncStatus)
    }

    @Test
    fun edit_kategori_baru_menulis_category_id_baru() = runBlocking {
        val id = repo.insert(draft("Makanan")).toInt()

        repo.update(
            FinancialRecord(id = id, description = "Nasi", amount = 20_000L, type = "expense", category = "Kuliner", date = 1_700_000_000_000L, notes = "")
        )

        assertEquals("cat:expense:kuliner", db.financialRecordDao().findById(id)!!.categoryId)
    }

    @Test
    fun edit_tidak_mengubah_status_sampah() = runBlocking {
        val id = repo.insert(draft("Makanan")).toInt()
        repo.delete(db.financialRecordDao().findById(id)!!)
        assertTrue(db.financialRecordDao().findById(id)!!.isDeleted)

        repo.update(FinancialRecord(id = id, description = "Bakso", amount = 25_000L, type = "expense", category = "Makanan", date = 1_700_000_000_000L, notes = ""))

        val after = db.financialRecordDao().findById(id)!!
        assertTrue("baris di Sampah tidak boleh aktif lagi karena diedit", after.isDeleted)
        assertEquals(SyncStatus.PENDING, after.syncStatus)
    }

    @Test
    fun hapus_menandai_pending_dan_menjaga_sync_key() = runBlocking {
        val id = repo.insert(draft("Makanan")).toInt()
        val original = db.financialRecordDao().findById(id)!!
        // simulating "sudah pernah tersinkron", baru dihapus user.
        db.financialRecordDao().markSynced(listOf(original.syncKey))

        repo.delete(original)

        val deleted = db.financialRecordDao().findById(id)!!
        assertTrue(deleted.isDeleted)
        assertEquals(SyncStatus.PENDING, deleted.syncStatus)
        assertEquals(original.syncKey, deleted.syncKey)
    }

    @Test
    fun backfill_mengisi_kategori_transaksi_lama() = runBlocking {
        // Baris hasil migrasi DB lama: kategori ada, tapi categoryId kosong.
        db.financialRecordDao().insertAll(
            listOf(
                FinancialRecord(description = "Bakso", amount = 25_000L, type = "expense", category = "Makanan", date = 1L, notes = "", updatedAt = 1L, syncKey = "t:legacy:1", syncStatus = SyncStatus.SYNCED),
                FinancialRecord(description = "Nasi", amount = 20_000L, type = "expense", category = "Kuliner", date = 1L, notes = "", updatedAt = 1L, syncKey = "t:legacy:2", syncStatus = SyncStatus.SYNCED),
                FinancialRecord(description = "Gaji", amount = 5_000_000L, type = "income", category = "Makanan", date = 1L, notes = "", updatedAt = 1L, syncKey = "t:legacy:3", syncStatus = SyncStatus.SYNCED)
            )
        )

        val filled = repo.backfillCategoryIds()

        assertEquals("dua expense yang punya kategori cocok", 2, filled)
        assertEquals("cat:expense:makanan", db.financialRecordDao().findBySyncKey("t:legacy:1")!!.categoryId)
        assertEquals("cat:expense:kuliner", db.financialRecordDao().findBySyncKey("t:legacy:2")!!.categoryId)
        assertEquals("", db.financialRecordDao().findBySyncKey("t:legacy:3")!!.categoryId)
    }

    @Test
    fun backfill_idempoten() = runBlocking {
        db.financialRecordDao().insertAll(
            listOf(FinancialRecord(description = "Bakso", amount = 25_000L, type = "expense", category = "Makanan", date = 1L, notes = "", updatedAt = 1L, syncKey = "t:legacy:1", syncStatus = SyncStatus.SYNCED))
        )

        assertEquals(1, repo.backfillCategoryIds())
        assertEquals("sudah terisi, tidak boleh dihitung lagi", 0, repo.backfillCategoryIds())
    }

    @Test
    fun apply_remote_mencocokkan_lewat_sync_key_bukan_id_lokal() = runBlocking {
        val localId = repo.insert(draft("Makanan")).toInt()
        val local = db.financialRecordDao().findById(localId)!!
        db.financialRecordDao().markSynced(listOf(local.syncKey))

        repo.applyRemote(
            local.copy(description = "Dari perangkat lain", updatedAt = local.updatedAt + 1_000L, syncStatus = SyncStatus.SYNCED)
        )

        val after = db.financialRecordDao().findById(localId)!!
        assertEquals("Dari perangkat lain", after.description)
        assertEquals(1, db.financialRecordDao().getAllRecordsWithDeleted().first().size)
    }

    @Test
    fun apply_remote_menambah_baris_baru_bila_sync_key_baru() = runBlocking {
        repo.insert(draft("Makanan"))

        repo.applyRemote(
            FinancialRecord(
                id = 0,
                description = "Dari HP lain",
                amount = 15_000L,
                type = "expense",
                category = "Kuliner",
                categoryId = "cat:expense:kuliner",
                date = 1_700_000_000_000L,
                notes = "",
                updatedAt = 1_700_000_000_000L,
                syncKey = "t:dari-hp-lain",
                syncStatus = SyncStatus.SYNCED
            )
        )

        val pulled = db.financialRecordDao().findBySyncKey("t:dari-hp-lain")
        assertNotNull(pulled)
        assertEquals("Dari HP lain", pulled!!.description)
        assertEquals(2, db.financialRecordDao().getRecordCount())
    }

    @Test
    fun nominal_nol_ditolak() = runBlocking {
        val result = runCatching { repo.insert(draft("Makanan").copy(amount = 0L)) }
        assertTrue("nominal 0 harus ditolak", result.isFailure)
    }

    @Test
    fun kategori_kosong_ditolak() = runBlocking {
        val result = runCatching { repo.insert(draft("  ")) }
        assertTrue(result.isFailure)
    }

    @Test
    fun kategori_tidak_dikenal_tetap_tersimpan_tanpa_id() = runBlocking {
        val id = repo.insert(draft("Kategori Aneh")).toInt()

        val saved = db.financialRecordDao().findById(id)!!
        assertEquals("", saved.categoryId)
        assertFalse("nama kategori tetap disimpan sebagai cache", saved.category.isBlank())
    }
}

