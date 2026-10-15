package com.example.ui.calendar

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Navigasi bulan tidak boleh memunculkan tanggal "hantu" dari bulan lain:
 * tanggal terpilih di-clamp ke panjang bulan tujuan.
 */
class CalendarDateClampTest {

    @Test
    fun `31_januari_menjadi_28_februari_di_tahun_bukan_kabisat`() {
        assertEquals(
            LocalDate.of(2026, 2, 28),
            CalendarViewModel.clampedDateForMonth(LocalDate.of(2026, 1, 31), YearMonth.of(2026, 2))
        )
    }

    @Test
    fun `31_januari_menjadi_29_februari_di_tahun_kabisat`() {
        assertEquals(
            LocalDate.of(2028, 2, 29),
            CalendarViewModel.clampedDateForMonth(LocalDate.of(2028, 1, 31), YearMonth.of(2028, 2))
        )
    }

    @Test
    fun `tanggal_yang_masih_ada_di_bulan_tujuan_tidak_berubah`() {
        assertEquals(
            LocalDate.of(2026, 3, 15),
            CalendarViewModel.clampedDateForMonth(LocalDate.of(2026, 1, 15), YearMonth.of(2026, 3))
        )
    }
}
