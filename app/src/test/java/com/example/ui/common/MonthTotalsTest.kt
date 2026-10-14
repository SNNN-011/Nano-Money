package com.example.ui.common

import com.example.data.FinancialRecord
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * PENDAPATAN dan PENGELUARAN di header dashboard mengikuti bulan yang dipilih,
 * sedangkan SALDO TOTAL tetap kumulatif sepanjang riwayat.
 *
 * Keduanya dulu ikut difilter per bulan, jadi saldo total ikut berubah kalau user
 * memutar bulan. Sekarang dipisah supaya kedua angka punya arti yang berbeda.
 */
class MonthTotalsTest {

    private val januaryStart = 1_735_689_600_000L
    private val januaryEnd = januaryStart + 30L * 24 * 60 * 60 * 1000
    private val decemberStart = 1_704_067_200_000L
    private val decemberEnd = januaryStart - 1L

    private fun record(type: String, amount: Long, date: Long) = FinancialRecord(
        description = "Uji",
        amount = amount,
        type = type,
        category = "Lainnya",
        date = date,
        notes = ""
    )

    @Test
    fun `hanya_transaksi_dalam_bulan_yang_dihitung`() {
        val totals = computeMonthTotals(
            listOf(
                record("income", 5_000_000L, januaryStart + 86_400_000L),
                record("expense", 250_000L, januaryStart + 172_800_000L),
                // Desember: di luar rentang.
                record("income", 9_000_000L, decemberStart + 86_400_000L),
                record("expense", 1_750_000L, decemberStart + 172_800_000L)
            ),
            startMs = januaryStart,
            endMs = januaryEnd
        )

        assertEquals(5_000_000L, totals.income)
        assertEquals(250_000L, totals.expense)
    }

    @Test
    fun `batas_bulan_inklusif_di_kedua_ujung`() {
        val totals = computeMonthTotals(
            listOf(
                record("income", 1_000L, januaryStart),
                record("expense", 500L, januaryEnd)
            ),
            startMs = januaryStart,
            endMs = januaryEnd
        )

        assertEquals(1_000L, totals.income)
        assertEquals(500L, totals.expense)
    }

    @Test
    fun `tipe_terapit_diabaikan`() {
        val totals = computeMonthTotals(
            listOf(
                record("income", 1_000L, januaryStart + 1_000L),
                record("expense", 500L, januaryStart + 1_000L),
                record("transfer", 999_999L, januaryStart + 1_000L)
            ),
            startMs = januaryStart,
            endMs = januaryEnd
        )

        assertEquals(1_000L, totals.income)
        assertEquals(500L, totals.expense)
    }

    @Test
    fun `rentang_kosong_nol_bukan_nol_negatif`() {
        val totals = computeMonthTotals(emptyList(), januaryStart, januaryEnd)

        assertEquals(0L, totals.income)
        assertEquals(0L, totals.expense)
    }

    @Test
    fun `saldo_total_tidak_ikut_bulan_sedangkan_pengeluaran_ikut`() {
        val records = listOf(
            record("income", 9_000_000L, decemberStart + 86_400_000L),
            record("income", 5_000_000L, januaryStart + 86_400_000L),
            record("expense", 1_750_000L, decemberStart + 172_800_000L),
            record("expense", 250_000L, januaryStart + 172_800_000L)
        )

        val allTime = computeDashboardTotals(records)
        val january = computeMonthTotals(records, januaryStart, januaryEnd)

        // Saldo tetap seluruh riwayat walau sedang menampilkan Januari.
        assertEquals(14_000_000L, allTime.income)
        assertEquals(2_000_000L, allTime.expense)
        assertEquals(12_000_000L, allTime.balance)

        // Angka bawahnya hanya bulan terpilih.
        assertEquals(5_000_000L, january.income)
        assertEquals(250_000L, january.expense)
    }
}