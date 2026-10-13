package com.example.data.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Aturan konflik adalah jantung multi-device: versi yang menang ditentukan waktu,
 * bukan device mana yang kebetulan sync terakhir.
 */
class ConflictResolverTest {

    @Test
    fun `baris lokal yang belum ada selalu menerima versi remote`() {
        assertTrue(ConflictResolver.shouldApplyRemote(localUpdatedAt = null, remoteUpdatedAt = 100L))
    }

    @Test
    fun `remote lebih baru menang`() {
        assertTrue(ConflictResolver.shouldApplyRemote(localUpdatedAt = 100L, remoteUpdatedAt = 101L))
    }

    @Test
    fun `remote lebih lama ditolak`() {
        assertFalse(ConflictResolver.shouldApplyRemote(localUpdatedAt = 200L, remoteUpdatedAt = 101L))
    }

    @Test
    fun `updatedAt sama tidak menimpa lokal`() {
        assertFalse(ConflictResolver.shouldApplyRemote(localUpdatedAt = 100L, remoteUpdatedAt = 100L))
    }

    @Test
    fun `selisih jauh tetap remote menang`() {
        assertTrue(ConflictResolver.shouldApplyRemote(localUpdatedAt = 1L, remoteUpdatedAt = Long.MAX_VALUE))
    }
}