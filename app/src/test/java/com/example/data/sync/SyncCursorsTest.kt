package com.example.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * State sync lokal: uid akun terakhir, kursor, dan tombstone penghapusan cloud.
 * Salah kelola di sini membuat data akun lama bocor ke akun baru (K5) atau data
 * yang sudah dihapus hidup kembali (M4).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncCursorsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val prefs
        get() = context.getSharedPreferences("sync_meta", Context.MODE_PRIVATE)

    @Test
    fun uid_disimpan_dan_dibaca() {
        assertNull(SyncCursors.getLastUid(context))

        SyncCursors.setLastUid(context, "uid-a")

        assertEquals("uid-a", SyncCursors.getLastUid(context))
    }

    @Test
    fun reset_ganti_akun_menghapus_kursor_flag_dan_tombstone() {
        SyncCursors.set(context, "financial_records", 123L)
        SyncCursors.markAdopted(context, "financial_records")
        SyncCursors.addTombstones(context, "financial_records", listOf("doc-1"))
        SyncCursors.setLastUid(context, "uid-a")

        SyncCursors.resetForAccountChange(context)

        assertEquals(0L, SyncCursors.get(context, "financial_records"))
        assertFalse(SyncCursors.isAdopted(context, "financial_records"))
        assertTrue(SyncCursors.getTombstones(context, "financial_records").isEmpty())
        assertNull(SyncCursors.getLastUid(context))
    }

    @Test
    fun tombstone_dibaca_dan_yang_kedaluwarsa_dibuang() {
        // Entri kedaluwarsa disisipkan langsung: expiry-nya sudah lewat.
        prefs.edit()
            .putStringSet("last_sync_tombstone_financial_records", setOf("1|sudah-lama"))
            .commit()

        SyncCursors.addTombstones(context, "financial_records", listOf("doc-2", ""))

        assertEquals(setOf("doc-2"), SyncCursors.getTombstones(context, "financial_records"))
    }
}
