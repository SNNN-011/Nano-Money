package com.example.ui.common

import com.example.data.FinancialRecord
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Angka besar di header dashboard harus menjumlah SELURUH riwayat.
 *
 * Pertemuan ini karena versinya ikut difilter per bulan terpilih, jadi "saldo
 * total" berubah begitu user memutar ke bulan lain.
 */
class DashboardTotalsTest {

    private fun record(
        type: String,
        amount: Long,
        date: Long
    ) = FinancialRecord(
        description = "Uji $amount",
        amount = amount,
        type = type,
        category = "Lainnya",
        date = date,
        notes = ""
    )

    @Test
    fun `menjumlahkan_seluruh_bulan_bukan_hanya_bulan_terpilih`() {
        val january = 1_700_000_000_000L
        val december = 1_735_689_600_000L

        val totals = computeDashboardTotals(
            listOf(
                record("income", 5_000_000L, january),
                record("expense", 250_000L, january),
                record("income", 9_000_000L, december),
                record("expense", 1_750_000L, december)
            )
        )

        assertEquals(14_000_000L, totals.income)
        assertEquals(2_000_000L, totals.expense)
        assertEquals(12_000_000L, totals.balance)
    }

    @Test
    fun `tipe_terapit_tidak_ikut_dihitung`() {
        val totals = computeDashboardTotals(
            listOf(
                record("income", 1_000L, 1L),
                record("expense", 500L, 1L),
                // Tipe di luar income/expense harus diabaikan, bukan dianggap pengeluaran.
                record("transfer", 999_999L, 1L)
            )
        )

        assertEquals(1_000L, totals.income)
        assertEquals(500L, totals.expense)
        assertEquals(500L, totals.balance)
    }

    @Test
    fun `daftar_kosong_nol_bukan_saldo_minus`() {
        val totals = computeDashboardTotals(emptyList())

        assertEquals(0L, totals.income)
        assertEquals(0L, totals.expense)
        assertEquals(0L, totals.balance)
    }

    @Test
    fun `pengeluaran_lebih_besar_beri_saldo_negatif`() {
        val totals = computeDashboardTotals(
            listOf(
                record("income", 100_000L, 1L),
                record("expense", 750_000L, 1L)
            )
        )

        assertEquals(-650_000L, totals.balance)
    }
}
