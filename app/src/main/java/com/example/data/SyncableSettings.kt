package com.example.data

/**
 * Allow-list setting yang boleh masuk jalur sinkronisasi.
 *
 * Sengaja memakai daftar putih, bukan daftar hitam: hanya key di sini yang boleh
 * ditulis ke tabel `app_settings` dan dikirim ke Firestore. Kunci keamanan
 * (PIN, biometrik, security question) dan preferensi khusus perangkat tidak pernah
 * ada di sini, sehingga mustahil ikut terkirim ke cloud.
 */
object SyncableSettings {

    /** Anggaran bulanan (nilai Long rupiah). */
    const val MONTHLY_BUDGET_LIMIT = "monthly_budget_limit"

    /** Kunci yang boleh disinkronkan saat ini. */
    val ALLOW_LIST: Set<String> = setOf(MONTHLY_BUDGET_LIMIT)

    /** Kunci keamanan/perangkat yang wajib lokal; dipakai test sebagai penjaga. */
    val LOCAL_ONLY_KEYS: Set<String> = setOf(
        "saved_pin",
        "pin_enabled",
        "pin_salt",
        "pin_hash",
        "security_question",
        "security_answer",
        "security_question_answer",
        "answer_salt",
        "answer_hash",
        "biometric_enabled",
        "security_migration_completed_v1",
        "auto_backup_enabled",
        "auto_backup_interval",
        "permission_dialog_shown",
        "has_cleaned_emojis_categories"
    )

    fun isSyncable(key: String): Boolean = key in ALLOW_LIST

    fun allowListed(keys: Collection<String>): List<String> = keys.filter { isSyncable(it) }
}