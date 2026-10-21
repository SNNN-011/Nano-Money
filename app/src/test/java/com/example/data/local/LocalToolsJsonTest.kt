package com.example.data.local

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Daftar kategori yang dikirim ke model: ejaan user dipertahankan, urut, tanpa
 * duplikat. Urutannya stabil supaya model tidak diinisialisasi ulang terus.
 */
class LocalToolsJsonTest {

    @Test
    fun `kategori_dikirim_dengan_ejaan_user_dan_terurut`() {
        val json = LocalToolsJson.build(
            incomeCategories = listOf("Gaji", "Bonus"),
            expenseCategories = listOf("Makanan", "Kopi")
        )

        assertTrue(json, json.contains(""""enum":["Bonus","Gaji"]"""))
        assertTrue(json, json.contains(""""enum":["Kopi","Makanan"]"""))
    }

    @Test
    fun `duplikat_yang_beda_huruf_besar_kecil_dibuang`() {
        val json = LocalToolsJson.build(
            incomeCategories = listOf("Gaji", "gaji", " Gaji "),
            expenseCategories = emptyList()
        )

        assertTrue(json, json.contains(""""enum":["Gaji"]"""))
        // Tanpa kategori, enum tidak dikirim sama sekali (bentuk model lama).
        assertTrue(json, json.contains("""{"name":"catat_pengeluaran","parameters":{"type":"object","properties":{"amount":{"type":"integer"},"category":{"type":"string"},"item":{"type":"string"},"date":{"type":"string"}},"required":["category"]}}"""))
    }
}
