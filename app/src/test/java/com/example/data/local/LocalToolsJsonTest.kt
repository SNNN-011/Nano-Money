package com.example.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bentuk tools yang dikirim ke model harus PERSIS sama dengan yang dipakai
 * saat latihan (v5): dua alat, `category` ber-`enum`, `required` ["category"],
 * pemisah tanpa spasi. Kalau berubah, model menerima prefix yang tidak pernah
 * ia lihat -- dan itu persis yang membuat kategori benar anjlok di aplikasi.
 */
class LocalToolsJsonTest {

    @Test
    fun `hanya dua alat dan urutannya benar`() {
        val json = LocalToolsJson.build(listOf("gaji"), listOf("makanan"))
        assertTrue(json.startsWith("""[{"name":"catat_pengeluaran""""))
        assertTrue(json.contains(""""name":"catat_masuk""""))
        assertFalse(json.contains("tanya"))
    }

    @Test
    fun `pemisah tanpa spasi seperti render mesin`() {
        val json = LocalToolsJson.build(listOf("gaji"), listOf("makanan"))
        assertFalse(json.contains(", "))
        assertFalse(json.contains(": "))
    }

    @Test
    fun `required hanya category`() {
        val json = LocalToolsJson.build(listOf("gaji"), listOf("makanan"))
        assertTrue(json.contains(""""required":["category"]"""))
    }

    @Test
    fun `enum kategori masuk dan keluar terpisah`() {
        val json = LocalToolsJson.build(listOf("gaji", "bunga bank"), listOf("makanan"))
        val keluar = json.substringAfter("\"name\":\"catat_pengeluaran\"")
            .substringAfter("\"enum\":[").substringBefore("]")
        val masuk = json.substringAfter("\"name\":\"catat_masuk\"")
            .substringAfter("\"enum\":[").substringBefore("]")
        assertEquals(""""makanan"""", keluar)
        assertTrue(masuk.contains("\"gaji\""))
        assertTrue(masuk.contains("\"bunga bank\""))
    }

    @Test
    fun `daftar kosong berarti tanpa enum`() {
        val json = LocalToolsJson.build(emptyList(), emptyList())
        assertFalse(json.contains("enum"))
        assertTrue(json.contains(""""category":{"type":"string"}"""))
    }

    @Test
    fun `urutan daftar tidak berpengaruh ke prefix`() {
        val a = LocalToolsJson.build(listOf("gaji", "bunga"), listOf("makanan"))
        val b = LocalToolsJson.build(listOf("bunga", "gaji"), listOf("makanan"))
        assertEquals(a, b)
    }

    @Test
    fun `duplikat dan spasi dibuang`() {
        val json = LocalToolsJson.build(listOf(" gaji ", "gaji", "GAJI"), listOf("makanan"))
        assertEquals(1, Regex("\"gaji\"").findAll(json).count())
        assertFalse(json.contains("\" Gaji \""))
    }

    @Test
    fun `nama kategori dengan kutip dibackslash`() {
        val json = LocalToolsJson.build(emptyList(), listOf("\"makan\""))
        assertTrue(json.contains("\"\\\"makan\\\"\""))
    }
}
