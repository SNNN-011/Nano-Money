package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Aturan main soal kategori: ID stabil saat rename, budget tetap nyambung lewat ID,
 * dan kategori terakhir pada tipenya tidak boleh dihapus.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CategoryRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var repo: CategoryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefs = context.getSharedPreferences("cat_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        repo = CategoryRepository(db.categoryDao(), prefs)
    }

    @After
    fun tearDown() {
        prefs.edit().clear().commit()
        db.close()
    }

    private suspend fun seedExpense(vararg names: String) {
        db.categoryDao().upsertAll(
            names.map { name ->
                Category(
                    id = "cat:expense:${name.lowercase()}",
                    name = name,
                    type = CategoryMigration.TYPE_EXPENSE,
                    updatedAt = 1L,
                    syncKey = "cat:expense:${name.lowercase()}",
                    syncStatus = SyncStatus.SYNCED
                )
            }
        )
    }

    @Test
    fun rename_menahan_id_dan_menaikkan_updatedAt() = runBlocking {
        seedExpense("Makanan")
        val before = repo.getByType(CategoryMigration.TYPE_EXPENSE).single()

        assertTrue(repo.rename(before.id, "Kuliner"))
        val after = repo.findById(before.id)!!

        assertEquals(before.id, after.id)
        assertEquals(before.syncKey, after.syncKey)
        assertEquals("Kuliner", after.name)
        assertTrue(after.updatedAt > before.updatedAt)
        assertEquals(SyncStatus.PENDING, after.syncStatus)
    }

    @Test
    fun budget_tetap_nyambung_setelah_rename() = runBlocking {
        seedExpense("Makanan")
        val category = repo.getByType(CategoryMigration.TYPE_EXPENSE).single()
        db.categoryBudgetDao().upsertAll(
            listOf(
                CategoryBudget(
                    categoryId = category.id,
                    budgetLimit = 800_000L,
                    updatedAt = 1L,
                    syncKey = category.id,
                    syncStatus = SyncStatus.SYNCED
                )
            )
        )

        repo.rename(category.id, "Kuliner")

        val budget = db.categoryBudgetDao().findByCategoryId(category.id)
        assertNotNull("budget harus tetap menunjuk id yang sama", budget)
        assertEquals(800_000L, budget!!.budgetLimit)
    }

    @Test
    fun rename_ke_nama_yang_sudah_dipakai_ditolak() = runBlocking {
        seedExpense("Makanan", "Hiburan")

        assertFalse(repo.rename("cat:expense:makanan", "Hiburan"))
        assertEquals("Makanan", repo.findById("cat:expense:makanan")!!.name)
    }

    @Test
    fun kategori_terakhir_satu_tipe_tidak_bisa_dihapus() = runBlocking {
        seedExpense("Makanan")

        assertFalse(repo.softDelete("cat:expense:makanan"))
        assertEquals(false, repo.findById("cat:expense:makanan")!!.isDeleted)
    }

    @Test
    fun kategori_terhapus_tetap_ada_untuk_riwayat() = runBlocking {
        seedExpense("Makanan", "Hiburan")

        assertTrue(repo.softDelete("cat:expense:hiburan"))

        val deleted = repo.findById("cat:expense:hiburan")!!
        assertTrue(deleted.isDeleted)
        assertEquals(SyncStatus.PENDING, deleted.syncStatus)
        assertEquals(listOf("Makanan"), repo.getByType(CategoryMigration.TYPE_EXPENSE).map { it.name })
    }

    @Test
    fun tambah_kategori_baru_pakai_uuid_bukan_id_legacy() = runBlocking {
        val created = repo.add(CategoryMigration.TYPE_EXPENSE, "Kopi")

        assertNotNull(created)
        // Id hasil migrasi prefs deterministik ("cat:expense:kopi"); yang baru harus acak
        // supaya dua perangkat yang sama-sama menambah kategori tidak menabrak id.
        assertNotEquals(
            CategoryMigration.legacyId(CategoryMigration.TYPE_EXPENSE, "Kopi"),
            created!!.id
        )
        assertNotNull(
            "suffix harus UUID",
            runCatching { java.util.UUID.fromString(created.id.removePrefix("cat:")) }.getOrNull()
        )
        assertEquals(created.id, created.syncKey)
        assertEquals(SyncStatus.PENDING, created.syncStatus)
    }

    @Test
    fun nama_duplikat_dan_kosong_ditolak() = runBlocking {
        seedExpense("Makanan")

        assertNull(repo.add(CategoryMigration.TYPE_EXPENSE, "makanan"))
        assertNull(repo.add(CategoryMigration.TYPE_EXPENSE, "   "))
    }

    @Test
    fun name_by_id_mencerminkan_rename_terbaru() = runBlocking {
        seedExpense("Makanan")
        repo.rename("cat:expense:makanan", "Kuliner")

        val map = repo.nameById.first()
        assertEquals("Kuliner", map["cat:expense:makanan"])
    }

    @Test
    fun migrasi_prefs_hanya_sekali_tetapi_paksa_ulang_tetap_idempoten() = runBlocking {
        prefs.edit()
            .putString(CategoryMigration.PREF_INCOME_LIST, "Gaji,Bonus")
            .putString(CategoryMigration.PREF_EXPENSE_LIST, "Makanan,Transportasi")
            .putLong("category_budget_Makanan", 600_000L)
            .commit()

        assertTrue(CategoryRepository.runIfNeeded(db.categoryDao(), db.categoryBudgetDao(), prefs))
        val firstIds = db.categoryDao().getByType(CategoryMigration.TYPE_EXPENSE).map { it.id }

        // Jalankan ulang walau flag sudah terpasang: hasil barisnya harus sama.
        prefs.edit().remove(CategoryMigration.PREF_MIGRATED_FLAG).commit()
        assertTrue(CategoryRepository.runIfNeeded(db.categoryDao(), db.categoryBudgetDao(), prefs))
        val secondIds = db.categoryDao().getByType(CategoryMigration.TYPE_EXPENSE).map { it.id }

        assertEquals(firstIds, secondIds)
        assertEquals(600_000L, db.categoryBudgetDao().findByCategoryId("cat:expense:makanan")!!.budgetLimit)
    }

    @Test
    fun migrasi_prefs_sudah_jalan_langsung_dilewati() = runBlocking {
        prefs.edit().putBoolean(CategoryMigration.PREF_MIGRATED_FLAG, true).commit()

        assertFalse(CategoryRepository.runIfNeeded(db.categoryDao(), db.categoryBudgetDao(), prefs))
        assertEquals(0, repo.countActive())
    }
}
