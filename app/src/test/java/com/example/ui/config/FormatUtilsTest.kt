package com.example.ui.config

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Format ringkas nominal: "Rp 5 jt", bukan "Rp 5,0 jt".
 *
 * Bug lama: `String.format("%.1f")` dengan locale id-ID menghasilkan "5,0",
 * sementara kode hanya membuang suffix ".0" (titik), jadi ",0" selalu tersisa.
 */
class FormatUtilsTest {

    @Test
    fun `nominal_bulat_tidak_menampilkan_desimal_nol`() {
        assertEquals("Rp 5 jt", FormatUtils.formatRupiahCompact(5_000_000L))
        assertEquals("Rp 1 rb", FormatUtils.formatRupiahCompact(1_000L))
        assertEquals("Rp 2 M", FormatUtils.formatRupiahCompact(2_000_000_000L))
    }

    @Test
    fun `nominal_pecahan_tetap_menampilkan_satu_desimal`() {
        assertEquals("Rp 1,2 jt", FormatUtils.formatRupiahCompact(1_200_000L))
        assertEquals("Rp 1,5 M", FormatUtils.formatRupiahCompact(1_500_000_000L))
    }

    @Test
    fun `di_bawah_seribu_memakai_format_rupiah_biasa`() {
        assertEquals("Rp 500", FormatUtils.formatRupiahCompact(500L))
    }
}
