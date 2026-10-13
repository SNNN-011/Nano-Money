package com.example.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deteksi dokumen legacy menentukan apakah data lama di cloud bisa dipulihkan.
 * Dokumen lama tidak punya updatedAt/syncKey, jadi tanpa adopsi ini tidak akan pernah
 * terbawa query `updatedAt > cursor`.
 */
class LegacyAdoptionTest {

    @Test
    fun `dokumen tanpa updatedAt dan syncKey dianggap legacy`() {
        val legacy = mapOf<String, Any?>(
            "id" to 12L,
            "description" to "Bakso",
            "amount" to 25000L,
            "type" to "expense",
            "category" to "Makanan",
            "date" to 1700000000000L,
            "notes" to "",
            "isDeleted" to false
        )
        assertTrue(LegacyAdoption.isLegacyDocument(legacy))
    }

    @Test
    fun `dokumen baru tidak dianggap legacy`() {
        val modern = mapOf<String, Any?>(
            "syncKey" to "t:uuid",
            "updatedAt" to 1700000000000L,
            "isDeleted" to false
        )
        assertFalse(LegacyAdoption.isLegacyDocument(modern))
    }

    @Test
    fun `dokumen yang hanya punya updatedAt dianggap sudah baru`() {
        val partial = mapOf<String, Any?>("updatedAt" to 1700000000000L)
        assertFalse(LegacyAdoption.isLegacyDocument(partial))
    }

    @Test
    fun `kunci legacy memakai id dokumen itu sendiri agar tidak membuat dokumen kedua`() {
        assertEquals("42", LegacyAdoption.legacySyncKey("42"))
        assertEquals("abc", LegacyAdoption.legacySyncKey("abc"))
    }

    @Test
    fun `dokumen kosong dianggap legacy agar tidak menggantung`() {
        assertTrue(LegacyAdoption.isLegacyDocument(emptyMap()))
    }
}
