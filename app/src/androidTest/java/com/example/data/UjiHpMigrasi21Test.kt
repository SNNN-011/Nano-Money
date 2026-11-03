package com.example.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifikasi migrasi 8 -> 9 pada database asli di HP.
 *
 * Bukan unit test: membaca database terenkripsi milik aplikasi yang sudah
 * terpasang, jadi hanya bisa jalan lewat instrumentasi.
 *
 * Jalankan:
 *   adb shell am instrument -w -e class com.example.data.UjiHpMigrasi21Test \
 *     com.aistudio.pencatatkeuangan.nnomny.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class UjiHpMigrasi21Test {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = AppDatabase.getDatabase(context)

    @Test
    fun migrasi_v9_menghasilkan_21_kategori_bawaan() {
        val versi = db.openHelper.readableDatabase
            .query("PRAGMA user_version")
            .use { if (it.moveToFirst()) it.getInt(0) else -1 }
        assertEquals(9, versi)

        val income = runBlocking { db.categoryDao().getByType(CategoryMigration.TYPE_INCOME) }
        val expense = runBlocking { db.categoryDao().getByType(CategoryMigration.TYPE_EXPENSE) }

        val namaIncome = income.map { it.name }
        val namaExpense = expense.map { it.name }

        // Kategori user/sinkron lama (mis. "Pakaian") memang dipertahankan;
        // yang wajib adalah semua default baru ada dan nama lama sudah berganti.
        assertTrue("income kurang: ${CategoryMigration.DEFAULT_INCOME - namaIncome.toSet()}",
            namaIncome.containsAll(CategoryMigration.DEFAULT_INCOME))
        assertTrue("expense kurang: ${CategoryMigration.DEFAULT_EXPENSE - namaExpense.toSet()}",
            namaExpense.containsAll(CategoryMigration.DEFAULT_EXPENSE))
        println("MIGRASI21 ekstra income=${namaIncome - CategoryMigration.DEFAULT_INCOME.toSet()}")
        println("MIGRASI21 ekstra expense=${namaExpense - CategoryMigration.DEFAULT_EXPENSE.toSet()}")

        // Nama lama harus sudah berganti, bukan hidup berdampingan.
        assertTrue("Makanan lama masih ada", "Makanan" !in namaExpense)
        assertTrue("Tagihan lama masih ada", "Tagihan" !in namaExpense)
        assertTrue("Makanan & Minuman hilang", "Makanan & Minuman" in namaExpense)
        assertTrue("Tagihan & Utilitas hilang", "Tagihan & Utilitas" in namaExpense)

        // Tidak boleh ada nama ganda dalam satu jenis.
        assertEquals(namaIncome.size, namaIncome.toSet().size)
        assertEquals(namaExpense.size, namaExpense.toSet().size)

        println("MIGRASI21 income=${namaIncome.sorted()}")
        println("MIGRASI21 expense=${namaExpense.sorted()}")
    }

    @Test
    fun categoryId_transaksi_dan_budget_masih_valid() {
        val semuaId = runBlocking { db.categoryDao().getAllIds() }.toSet()

        val idTerpakai = mutableListOf<String>()
        db.openHelper.readableDatabase
            .query("SELECT DISTINCT categoryId FROM financial_records WHERE categoryId != ''")
            .use { c ->
                while (c.moveToNext()) idTerpakai += c.getString(0)
            }
        val idBudget = mutableListOf<String>()
        db.openHelper.readableDatabase
            .query("SELECT DISTINCT categoryId FROM category_budgets WHERE categoryId != ''")
            .use { c ->
                while (c.moveToNext()) idBudget += c.getString(0)
            }

        val yatim = (idTerpakai + idBudget).filter { it !in semuaId }
        assertTrue("categoryId yatim: $yatim", yatim.isEmpty())

        println("MIGRASI21 kategori=${semuaId.size} transaksi=${idTerpakai.size} budget=${idBudget.size}")
    }
}
