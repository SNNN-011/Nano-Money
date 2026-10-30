package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Migrasi prefs -> Room harus murni dan idempoten. Idempoten itu wajib: dua perangkat
 * yang bermigrasi dari prefs sama harus menghasilkan categoryId sama supaya tidak
 * ada kategori ganda setelah keduanya sync.
 */
class CategoryMigrationTest {

    private val now = 1_700_000_000_000L

    @Test
    fun daftar_kosong_fallback_ke_default() {
        val categories = CategoryMigration.buildCategories(null, "  , ,", now)

        assertEquals(CategoryMigration.DEFAULT_INCOME.size + CategoryMigration.DEFAULT_EXPENSE.size, categories.size)
        assertTrue(categories.any { it.name == "Gaji" && it.type == CategoryMigration.TYPE_INCOME })
        assertTrue(categories.any { it.name == "Makanan & Minuman" && it.type == CategoryMigration.TYPE_EXPENSE })
        assertEquals(8, CategoryMigration.DEFAULT_INCOME.size)
        assertEquals(13, CategoryMigration.DEFAULT_EXPENSE.size)
    }

    @Test
    fun upgrade_mengganti_nama_bawaan_lama_tanpa_mengubah_id() {
        val lama = listOf(
            Category("cat:expense:makanan", "Makanan", CategoryMigration.TYPE_EXPENSE, updatedAt = 1L)
        )
        val plan = CategoryMigration.planDefaultUpgrade(lama, now)

        val rename = plan.renames.single()
        assertEquals("cat:expense:makanan", rename.id)
        assertEquals("Makanan & Minuman", rename.name)
        assertEquals(now, rename.updatedAt)
        // Penerus "Tagihan" belum ada di data, jadi harus ikut ditambahkan.
        assertTrue(plan.inserts.any { it.name == "Tagihan & Utilitas" })
        // Nama yang sudah diwakili rename tidak boleh ditambahkan lagi.
        assertTrue(plan.inserts.none { it.name == "Makanan & Minuman" })
        assertEquals(20, plan.inserts.size)
    }

    @Test
    fun upgrade_tidak_menyentuh_kategori_yang_sudah_diganti_nama_user() {
        // id masih id legacy, tapi namanya sudah diubah user: jangan ditimpa.
        val lama = listOf(
            Category("cat:expense:makanan", "Food", CategoryMigration.TYPE_EXPENSE, updatedAt = 1L)
        )
        val plan = CategoryMigration.planDefaultUpgrade(lama, now)

        assertTrue(plan.renames.isEmpty())
        assertTrue(plan.inserts.any { it.name == "Makanan & Minuman" })
    }

    @Test
    fun upgrade_tidak_menghidupkan_kategori_yang_sudah_dihapus() {
        val lama = listOf(
            Category("cat:expense:makanan", "Makanan", CategoryMigration.TYPE_EXPENSE, isDeleted = true)
        )
        val plan = CategoryMigration.planDefaultUpgrade(lama, now)

        assertTrue(plan.renames.isEmpty())
        assertTrue(plan.inserts.none { it.name == "Makanan & Minuman" })
        // Kategori lain yang belum pernah ada tetap ditambahkan.
        assertTrue(plan.inserts.any { it.name == "Kebutuhan Rumah" })
    }

    @Test
    fun upgrade_tidak_menduplikasi_nama_yang_sudah_ada() {
        val lama = listOf(
            Category("cat:expense:makanan", "Makanan", CategoryMigration.TYPE_EXPENSE, updatedAt = 1L),
            Category("buatan-user", "Makanan & Minuman", CategoryMigration.TYPE_EXPENSE, updatedAt = 2L)
        )
        val plan = CategoryMigration.planDefaultUpgrade(lama, now)

        assertTrue(plan.renames.isEmpty())
        assertTrue(plan.inserts.none { it.name == "Makanan & Minuman" })
    }

    @Test
    fun upgrade_idempoten() {
        val lama = listOf(
            Category("cat:expense:makanan", "Makanan", CategoryMigration.TYPE_EXPENSE, updatedAt = 1L),
            Category("cat:expense:hiburan", "Hiburan", CategoryMigration.TYPE_EXPENSE, updatedAt = 1L)
        )
        val plan = CategoryMigration.planDefaultUpgrade(lama, now)
        val sesudah = lama
            .filterNot { row -> plan.renames.any { it.id == row.id } } +
            plan.renames + plan.inserts

        val kedua = CategoryMigration.planDefaultUpgrade(sesudah, now + 1L)
        assertTrue(kedua.renames.isEmpty())
        assertTrue(kedua.inserts.isEmpty())
    }

    @Test
    fun budget_lama_menemukan_kategori_yang_sudah_diganti_nama() {
        val categories = CategoryMigration.buildCategories(
            null, null, now
        )
        val (budgets, orphans) = CategoryMigration.buildBudgets(
            budgetByName = mapOf("Makanan" to 750_000L, "Tagihan" to 400_000L),
            categories = categories
        )

        assertEquals(emptyList<String>(), orphans)
        assertEquals(
            setOf("cat:expense:makanan-minuman", "cat:expense:tagihan-utilitas"),
            budgets.map { it.categoryId }.toSet()
        )
    }

    @Test
    fun csv_memotong_spasi_dan_menghapus_duplikat() {
        assertEquals(
            listOf("Makanan", "Transport", "Hiburan"),
            CategoryMigration.parseCategoryList(" Makanan , Transport,Makanan, ,Hiburan ")
        )
    }

    @Test
    fun id_legacy_deterministik_antar_perangkat() {
        val a = CategoryMigration.buildCategories("Gaji", "Makanan & Minum", now)
        val b = CategoryMigration.buildCategories("Gaji", "Makanan & Minum", now + 99_999L)

        assertEquals(a.map { it.id }, b.map { it.id })
        assertEquals("cat:income:gaji", a.first { it.type == CategoryMigration.TYPE_INCOME }.id)
        // "Makanan & Minum" -> "makanan-minum"
        assertEquals(
            "cat:expense:makanan-minum",
            a.first { it.type == CategoryMigration.TYPE_EXPENSE }.id
        )
    }

    @Test
    fun hasil_migrasi_selalu_pending_dan_sync_key_sama_dengan_id() {
        val categories = CategoryMigration.buildCategories("Gaji", "Makanan", now)
        categories.forEach { category ->
            assertEquals(SyncStatus.PENDING, category.syncStatus)
            assertEquals(category.id, category.syncKey)
            assertEquals(now, category.updatedAt)
            assertEquals(false, category.isDeleted)
        }
    }

    @Test
    fun budget_dipetakan_ke_category_id_bukan_nama() {
        val categories = CategoryMigration.buildCategories("Gaji", "Makanan,Transportasi", now)
        val (budgets, orphans) = CategoryMigration.buildBudgets(
            budgetByName = mapOf("Makanan" to 750_000L, "Transportasi" to 250_000L),
            categories = categories
        )

        assertEquals(emptyList<String>(), orphans)
        assertEquals(2, budgets.size)
        val makanan = budgets.single { it.budgetLimit == 750_000L }
        assertEquals("cat:expense:makanan", makanan.categoryId)
        assertEquals(SyncStatus.PENDING, makanan.syncStatus)
    }

    @Test
    fun budget_yatim_dibuang_dan_budget_nol_diabaikan() {
        val categories = CategoryMigration.buildCategories("Gaji", "Makanan", now)
        val (budgets, orphans) = CategoryMigration.buildBudgets(
            budgetByName = mapOf("Hiburan" to 100_000L, "Makanan" to 0L, "  " to 10L),
            categories = categories
        )

        assertEquals(emptyList<Any>(), budgets)
        assertEquals(listOf("Hiburan"), orphans)
    }

    @Test
    fun pref_budget_diterima_untuk_semua_tipe_numerik() {
        val collected = CategoryMigration.collectBudgetPrefs(
            mapOf(
                "category_budget_makanan" to 500L,
                "category_budget_transportasi" to 250.0,
                "income_categories_list" to "Gaji",
                "saved_pin" to "1234"
            )
        )

        assertEquals(mapOf("makanan" to 500L, "transportasi" to 250L), collected)
    }

    @Test
    fun dua_kali_migrasi_menghasilkan_baris_yang_sama() {
        val first = CategoryMigration.buildCategories("Gaji", "Makanan", now)
        val second = CategoryMigration.buildCategories("Gaji", "Makanan", now)
        assertEquals(first, second)

        val (b1, _) = CategoryMigration.buildBudgets(mapOf("Makanan" to 100L), first)
        val (b2, _) = CategoryMigration.buildBudgets(mapOf("Makanan" to 100L), second)
        assertEquals(b1.map { it.categoryId }, b2.map { it.categoryId })
    }
}