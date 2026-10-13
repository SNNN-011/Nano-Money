package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Setting hanya boleh lewat jalur sync kalau key-nya ada di allow-list, baik saat
 * menulis lokal maupun saat menerima versi remote.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppSettingsRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var repo: AppSettingsRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefs = context.getSharedPreferences("settings_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        repo = AppSettingsRepository(db.appSettingDao())
    }

    @After
    fun tearDown() {
        prefs.edit().clear().commit()
        db.close()
    }

    @Test
    fun set_anggaran_bulanan_tersimpan_sebagai_long_pending() = runBlocking {
        repo.setLong(SyncableSettings.MONTHLY_BUDGET_LIMIT, 1_500_000L)

        val stored = repo.find(SyncableSettings.MONTHLY_BUDGET_LIMIT)!!
        assertEquals(1_500_000L, stored.valueLong)
        assertNull(stored.valueText)
        assertEquals(SyncableSettings.MONTHLY_BUDGET_LIMIT, stored.syncKey)
        assertEquals(SyncStatus.PENDING, stored.syncStatus)
        assertEquals(1_500_000L, repo.getLong(SyncableSettings.MONTHLY_BUDGET_LIMIT))
    }

    @Test(expected = IllegalArgumentException::class)
    fun set_key_terlarang_melempar_eksepsi() = runBlocking {
        repo.setLong("saved_pin", 1234L)
    }

    @Test
    fun apply_remote_menolak_key_terlarang() = runBlocking {
        repo.applyRemote(
            AppSetting(
                key = "pin_hash",
                valueText = "rahasia",
                updatedAt = 9L,
                syncKey = "pin_hash",
                syncStatus = SyncStatus.SYNCED
            )
        )

        assertNull(repo.find("pin_hash"))
        assertTrue(db.appSettingDao().getPendingSync().isEmpty())
    }

    @Test
    fun apply_remote_menyimpan_versi_klien_tanpa_menimpa_updatedAt() = runBlocking {
        repo.applyRemote(
            AppSetting(
                key = SyncableSettings.MONTHLY_BUDGET_LIMIT,
                valueLong = 2_000_000L,
                updatedAt = 4_242L,
                syncKey = SyncableSettings.MONTHLY_BUDGET_LIMIT,
                syncStatus = SyncStatus.SYNCED
            )
        )

        val stored = repo.find(SyncableSettings.MONTHLY_BUDGET_LIMIT)!!
        assertEquals(2_000_000L, stored.valueLong)
        assertEquals(4_242L, stored.updatedAt)
        assertEquals(SyncStatus.SYNCED, stored.syncStatus)
    }

    @Test
    fun migrasi_prefs_hanya_mengambil_key_allow_list() = runBlocking {
        prefs.edit()
            .putLong(SyncableSettings.MONTHLY_BUDGET_LIMIT, 750_000L)
            .putString("saved_pin", "4321")
            .putBoolean("biometric_enabled", true)
            .commit()

        assertTrue(AppSettingsRepository.runIfNeeded(db.appSettingDao(), prefs))

        assertEquals(750_000L, repo.getLong(SyncableSettings.MONTHLY_BUDGET_LIMIT))
        assertNull("pin tidak boleh masuk tabel sync", repo.find("saved_pin"))
        assertNull("biometrik tidak boleh masuk tabel sync", repo.find("biometric_enabled"))
    }

    @Test
    fun migrasi_sudah_jalan_dilewati() = runBlocking {
        prefs.edit()
            .putBoolean(AppSettingsRepository.PREF_MIGRATED_FLAG, true)
            .putLong(SyncableSettings.MONTHLY_BUDGET_LIMIT, 750_000L)
            .commit()

        assertFalse(AppSettingsRepository.runIfNeeded(db.appSettingDao(), prefs))
        assertEquals(0L, repo.getLong(SyncableSettings.MONTHLY_BUDGET_LIMIT, default = 0L))
    }
}
