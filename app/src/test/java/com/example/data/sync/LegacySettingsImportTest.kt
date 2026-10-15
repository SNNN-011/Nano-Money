package com.example.data.sync

import com.example.data.CategoryMigration
import com.example.data.SyncableSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Impor dokumen preferensi lawas harus memulihkan kategori kustom + anggaran, tapi
 * tidak boleh pernah menyentuh kunci keamanan yang kebetulan ada di dokumen itu.
 */
class LegacySettingsImportTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `kunci keamanan_tidak_lolos_ke_impor`() {
        val doc = mapOf(
            "income_categories_list" to "Gaji,Bonus",
            "expense_categories_list" to "Makanan",
            "category_budget_Makanan" to 500_000L,
            "saved_pin" to "1234",
            "pin_hash" to "abc",
            "biometric_enabled" to true,
            "security_question" to "Nama ibu?",
            "auto_backup_interval" to 24L
        )

        val allowed = LegacySettingsImport.onlyAllowedFields(doc)

        assertEquals(
            setOf("income_categories_list", "expense_categories_list", "category_budget_Makanan"),
            allowed.keys
        )
        assertFalse("PIN tidak boleh ikut diimpor", allowed.containsKey("saved_pin"))
        assertFalse("hash PIN tidak boleh ikut", allowed.containsKey("pin_hash"))
        assertFalse("biometrik tidak boleh ikut", allowed.containsKey("biometric_enabled"))
    }

    @Test
    fun `kategori_kustom_dari_cloud_dipulihkan`() {
        val imported = LegacySettingsImport.parse(
            docData = mapOf(
                "income_categories_list" to "Gaji,Bonus NSW",
                "expense_categories_list" to "Makanan,Kopi"
            ),
            existingCategoryIds = emptySet(),
            now = now
        )

        assertEquals(
            listOf("cat:income:gaji", "cat:income:bonus-nsw", "cat:expense:makanan", "cat:expense:kopi"),
            imported.categories.map { it.id }
        )
        assertTrue(imported.categories.all { it.syncStatus == com.example.data.SyncStatus.PENDING })
    }

    @Test
    fun `kategori_lokal_yang_sudah_ada_tidak_ditimpa_nama_lama_dari_cloud`() {
        val imported = LegacySettingsImport.parse(
            docData = mapOf("expense_categories_list" to "Makanan"),
            // Perangkat ini sudah punya kategori itu, mungkin sudah di-rename.
            existingCategoryIds = setOf("cat:expense:makanan"),
            now = now
        )

        assertTrue("kategori yang sudah ada tidak boleh diimpor ulang", imported.categories.isEmpty())
    }

    @Test
    fun `anggaran_dipasang_ke_kategori_yang_tersedia`() {
        val imported = LegacySettingsImport.parse(
            docData = mapOf(
                "expense_categories_list" to "Makanan,Kopi",
                "category_budget_Makanan" to 750_000L,
                "category_budget_Kopi" to 300_000.0
            ),
            existingCategoryIds = emptySet(),
            now = now
        )

        assertEquals(2, imported.budgets.size)
        assertEquals("cat:expense:makanan", imported.budgets.first { it.budgetLimit == 750_000L }.categoryId)
        assertEquals("cat:expense:kopi", imported.budgets.first { it.budgetLimit == 300_000L }.categoryId)
    }

    @Test
    fun `anggaran_yatim_dilaporkan_bukan_dipasang`() {
        val imported = LegacySettingsImport.parse(
            docData = mapOf(
                "expense_categories_list" to "Makanan",
                "category_budget_Hiburan" to 100_000L
            ),
            existingCategoryIds = emptySet(),
            now = now
        )

        assertEquals(listOf("Hiburan"), imported.orphanBudgets)
        assertTrue(imported.budgets.isEmpty())
    }

    @Test
    fun `dokumen_tanpa_daftar_kategori_tidak_mengarang_kategori_default`() {
        val imported = LegacySettingsImport.parse(
            docData = emptyMap(),
            existingCategoryIds = emptySet(),
            now = now
        )

        assertTrue("dari cloud tidak boleh mengarang kategori", imported.categories.isEmpty())
        assertTrue(imported.budgets.isEmpty())
    }

    @Test
    fun `hanya_daftar_yang_ada_di_dokumen_yang_dipakai`() {
        val imported = LegacySettingsImport.parse(
            docData = mapOf("expense_categories_list" to "Makanan"),
            existingCategoryIds = emptySet(),
            now = now
        )

        // Tidak boleh ikut menyuntik daftar default income.
        assertEquals(listOf("cat:expense:makanan"), imported.categories.map { it.id })
    }

    @Test
    fun `hanya_ pref_kategori_yang_boleh_dibaca`() {
        assertTrue(LegacySettingsImport.isAllowedField(CategoryMigration.PREF_INCOME_LIST))
        assertTrue(LegacySettingsImport.isAllowedField(CategoryMigration.PREF_EXPENSE_LIST))
        assertTrue(LegacySettingsImport.isAllowedField("category_budget_Makanan"))
        assertTrue("M5: anggaran bulanan ikut diimpor", LegacySettingsImport.isAllowedField(SyncableSettings.MONTHLY_BUDGET_LIMIT))
        assertFalse(LegacySettingsImport.isAllowedField("saved_pin"))
        assertFalse(LegacySettingsImport.isAllowedField("auto_backup_interval"))
    }

    @Test
    fun `anggaran_bulanan_dari_dokumen_lama_ikut_diimpor`() {
        val imported = LegacySettingsImport.parse(
            docData = mapOf(SyncableSettings.MONTHLY_BUDGET_LIMIT to 1_500_000L),
            existingCategoryIds = emptySet(),
            now = now
        )

        assertEquals(1_500_000L, imported.monthlyBudgetLimit)
    }

    @Test
    fun `anggaran_bulanan_hilang_bukan_dianggap_nol`() {
        val imported = LegacySettingsImport.parse(
            docData = mapOf("expense_categories_list" to "Makanan"),
            existingCategoryIds = emptySet(),
            now = now
        )

        assertEquals(null, imported.monthlyBudgetLimit)
    }

    @Test
    fun daftar_kosong_di_cloud_tidak_mengisi_kategori_bawaan() {
        // CSV kosong berarti user memang menghapus semua kategorinya. Mengisinya
        // dengan DEFAULT_EXPENSE akan membangkitkan ulang semua kategori bawaan
        // yang sengaja sudah dihapus.
        val imporKosong = LegacySettingsImport.parse(
            docData = mapOf(
                CategoryMigration.PREF_INCOME_LIST to "",
                CategoryMigration.PREF_EXPENSE_LIST to ""
            ),
            existingCategoryIds = emptySet(),
            now = 1_000L
        )
        assertTrue("kategori tidak boleh dibangkitkan dari daftar kosong", imporKosong.categories.isEmpty())
    }

    @Test
    fun csv_hanya_spandau_tidak_ikut_jadi_kategori() {
        val imporSpasi = LegacySettingsImport.parse(
            docData = mapOf(CategoryMigration.PREF_EXPENSE_LIST to "   "),
            existingCategoryIds = emptySet(),
            now = 1_000L
        )
        assertTrue(imporSpasi.categories.isEmpty())
    }

    @Test
    fun daftar_asli_dari_cloud_masih_diimpor() {
        val impor = LegacySettingsImport.parse(
            docData = mapOf(
                CategoryMigration.PREF_INCOME_LIST to "Gaji",
                CategoryMigration.PREF_EXPENSE_LIST to "Perawatan Tubuh"
            ),
            existingCategoryIds = emptySet(),
            now = 1_000L
        )
        assertEquals(
            listOf("Gaji", "Perawatan Tubuh"),
            impor.categories.map { it.name }
        )
    }
}
