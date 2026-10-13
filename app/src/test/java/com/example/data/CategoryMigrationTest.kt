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
        assertTrue(categories.any { it.name == "Makanan" && it.type == CategoryMigration.TYPE_EXPENSE })
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