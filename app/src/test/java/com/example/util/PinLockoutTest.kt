package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * H3: jadwal lockout PIN harus stabil — penambahan tingkatan tidak boleh
 * tanpa sengaja melunakkan (atau mengeraskan) jeda yang sudah disepakati.
 */
class PinLockoutTest {

    @Test
    fun `lockout bertingkat sesuai jumlah gagal berurutan`() {
        // 0-4 gagal: belum terkunci
        assertEquals(0L, PinUtils.lockoutDurationMillis(0))
        assertEquals(0L, PinUtils.lockoutDurationMillis(4))

        // 5-9 gagal: 30 detik
        assertEquals(30_000L, PinUtils.lockoutDurationMillis(5))
        assertEquals(30_000L, PinUtils.lockoutDurationMillis(9))

        // 10-14 gagal: 5 menit
        assertEquals(5 * 60_000L, PinUtils.lockoutDurationMillis(10))
        assertEquals(5 * 60_000L, PinUtils.lockoutDurationMillis(14))

        // 15+ gagal: 30 menit
        assertEquals(30 * 60_000L, PinUtils.lockoutDurationMillis(15))
        assertEquals(30 * 60_000L, PinUtils.lockoutDurationMillis(99))
    }
}
