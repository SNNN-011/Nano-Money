package com.example.data

import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kunci sinkronisasi menentukan apakah dua perangkat menghasilkan dokumen yang sama.
 * Kunci transaksi hasil recurring wajib deterministik per bulan.
 */
class SyncKeysTest {

    private fun recurring(key: String) = RecurringTransaction(
        syncKey = key,
        description = "Internet",
        amount = 350_000L,
        type = "expense",
        category = "Tagihan",
        dayOfMonth = 5
    )

    @Test
    fun `kunci transaksi manual unik setiap kali`() {
        val keys = (1..500).map { SyncKeys.newTransactionKey() }.toSet()
        assertEquals(500, keys.size)
    }

    @Test
    fun `kunci transaksi manual berawalan t`() {
        assertTrue(SyncKeys.newTransactionKey().startsWith("t:"))
    }

    @Test
    fun `kunci transaksi berulang sama untuk perangkat berbeda pada bulan sama`() {
        val deviceA = SyncKeys.recurringGenerated(recurring("r:abc"), YearMonth.of(2026, 3))
        val deviceB = SyncKeys.recurringGenerated(recurring("r:abc"), YearMonth.of(2026, 3))
        assertEquals(deviceA, deviceB)
    }

    @Test
    fun `bulan berbeda menghasilkan kunci berbeda`() {
        val maret = SyncKeys.recurringGenerated(recurring("r:abc"), YearMonth.of(2026, 3))
        val april = SyncKeys.recurringGenerated(recurring("r:abc"), YearMonth.of(2026, 4))
        assertNotEquals(maret, april)
    }

    @Test
    fun `recurring berbeda menghasilkan kunci berbeda pada bulan sama`() {
        val a = SyncKeys.recurringGenerated(recurring("r:abc"), YearMonth.of(2026, 3))
        val b = SyncKeys.recurringGenerated(recurring("r:xyz"), YearMonth.of(2026, 3))
        assertNotEquals(a, b)
    }

    @Test
    fun `format kunci recurring mengikuti pola r key bulan`() {
        assertEquals(
            "r:r:abc:2026-03",
            SyncKeys.recurringGenerated("r:abc", YearMonth.of(2026, 3))
        )
    }
}