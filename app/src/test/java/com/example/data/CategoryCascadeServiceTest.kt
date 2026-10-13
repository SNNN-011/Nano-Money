package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Rename kategori harus ikut mengubah nama pada transaksi, dan kategori yang dihapus
 * harus memindahkan transaksinya ke kategori pengganti. Dua-duanya wajib PENDING
 * supaya ikut terkirim ke perangkat lain.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CategoryCascadeServiceTest {

    private lateinit var db: AppDatabase
    private lateinit var cascade: CategoryCascadeService

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        cascade = CategoryCascadeService(db.financialRecordDao())
    }

    @After
    fun tearDown() = db.close()

    private fun record(
        description: String,
        categoryId: String,
        categoryName: String,
        isDeleted: Boolean = false
    ) = FinancialRecord(
        description = description,
        amount = 25_000L,
        type = CategoryMigration.TYPE_EXPENSE,
        category = categoryName,
        categoryId = categoryId,
        date = 1_700_000_000_000L,
        notes = "",
        isDeleted = isDeleted,
        updatedAt = 1L,
        syncKey = "t:test:$description",
        syncStatus = SyncStatus.SYNCED
    )

    @Test
    fun rename_menulis_nama_baru_tanpa_mengganti_category_id() = runBlocking {
        val dao = db.financialRecordDao()
        dao.insertAll(
            listOf(
                record("Bakso", "cat:expense:makanan", "Makanan"),
                record("Nasi", "cat:expense:makanan", "Makanan"),
                record("Bensin", "cat:expense:transportasi", "Transportasi")
            )
        )

        val changed = cascade.applyRename("cat:expense:makanan", "Kuliner")
        assertEquals(2, changed)

        val bakso = dao.findBySyncKey("t:test:Bakso")!!
        assertEquals("Kuliner", bakso.category)
        assertEquals("kategori id harus tetap", "cat:expense:makanan", bakso.categoryId)
        assertEquals(SyncStatus.PENDING, bakso.syncStatus)
        assertTrue(bakso.updatedAt > 1L)

        // Kategori lain tidak tersentuh.
        assertEquals("Transportasi", dao.findBySyncKey("t:test:Bensin")!!.category)
        assertEquals(SyncStatus.SYNCED, dao.findBySyncKey("t:test:Bensin")!!.syncStatus)
    }

    @Test
    fun kategori_dihapus_memindahkan_transaksi_ke_kategori_pengganti() = runBlocking {
        val dao = db.financialRecordDao()
        dao.insertAll(
            listOf(
                record("Bakso", "cat:expense:makanan", "Makanan"),
                record("Nasi", "cat:expense:makanan", "Makanan")
            )
        )
        val lainnya = Category(id = "cat:expense:lainnya", name = "Lainnya", type = CategoryMigration.TYPE_EXPENSE)

        val moved = cascade.applyMove("cat:expense:makanan", lainnya)
        assertEquals(2, moved)

        val bakso = dao.findBySyncKey("t:test:Bakso")!!
        assertEquals("cat:expense:lainnya", bakso.categoryId)
        assertEquals("Lainnya", bakso.category)
        assertEquals(SyncStatus.PENDING, bakso.syncStatus)
        // Nominal dan status Sampah tidak boleh berubah.
        assertEquals(25_000L, bakso.amount)
        assertEquals(false, bakso.isDeleted)
    }

    @Test
    fun transaksi_di_sampah_tidak_dikutik_cascade() = runBlocking {
        val dao = db.financialRecordDao()
        dao.insertAll(listOf(record("Bakso", "cat:expense:makanan", "Makanan", isDeleted = true)))

        assertEquals(0, cascade.applyRename("cat:expense:makanan", "Kuliner"))

        val bakso = dao.findBySyncKey("t:test:Bakso")!!
        assertEquals("Makanan", bakso.category)
        assertEquals(SyncStatus.SYNCED, bakso.syncStatus)
    }
}
