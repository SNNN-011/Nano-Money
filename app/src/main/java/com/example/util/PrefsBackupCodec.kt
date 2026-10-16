package com.example.util

import android.content.SharedPreferences
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Serialisasi isi SharedPreferences menjadi teks portabel untuk cadangan.
 *
 * Prefs terenkripsi (EncryptedSharedPreferences) tidak bisa disalin mentah:
 * XML-nya dienkripsi dengan kunci Keystore yang terikat perangkat asal.
 * Nilainya dibaca lewat API lalu ditulis ulang di perangkat tujuan — di sana
 * akan otomatis dienkripsi ulang dengan Keystore perangkat tersebut.
 *
 * Format per baris: `tipe\u0002kunci\u0002nilai`
 * - tipe: s=String, b=Boolean(1/0), i=Int, l=Long, f=Float, t=StringSet
 * - kunci dan nilai String di-URL-encode supaya aman terhadap newline/tab.
 * - StringSet: item dipisah koma, tiap item di-encode lebih dulu.
 *
 * Kunci teknis library (keyset AndroidX) dan penanda migrasi internal
 * sengaja dilewati: keduanya dibuat ulang otomatis di perangkat tujuan.
 */
object PrefsBackupCodec {

    private const val SEP = "\u0002"
    private const val SKIP_PREFIX = "__androidx_security_crypto_"
    private const val SKIP_KEY_MIGRATION = "security_migration_completed_v1"

    /** Ubah seluruh isi prefs menjadi teks. */
    fun export(prefs: SharedPreferences): String = exportMap(prefs.all)

    /**
     * Tulis balik isi teks ke prefs. Hanya nilai yang berhasil diurai yang
     * ditulis; baris rusak dilewati. Mengembalikan jumlah entri yang ditulis.
     */
    fun import(prefs: SharedPreferences, data: String): Int {
        val entries = importMap(data)
        if (entries.isEmpty()) return 0
        val editor = prefs.edit()
        entries.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        editor.commit()
        return entries.size
    }

    // ---- Inti murni (diuji unit tanpa Android) ----

    internal fun exportMap(entries: Map<String, *>): String {
        val sb = StringBuilder()
        for ((key, value) in entries) {
            if (isSkippedKey(key)) continue
            val encodedKey = encode(key)
            when (value) {
                null -> continue
                is String -> appendLine(sb, "s", encodedKey, encode(value))
                is Boolean -> appendLine(sb, "b", encodedKey, if (value) "1" else "0")
                is Int -> appendLine(sb, "i", encodedKey, value.toString())
                is Long -> appendLine(sb, "l", encodedKey, value.toString())
                is Float -> appendLine(sb, "f", encodedKey, value.toString())
                is Set<*> -> {
                    val items = value.filterIsInstance<String>().joinToString(",") { encode(it) }
                    appendLine(sb, "t", encodedKey, items)
                }
                // Tipe lain tidak mungkin ada di SharedPreferences (hanya 6 tipe
                // di atas); kalau ada, lewati saja tanpa menggagalkan cadangan.
            }
        }
        return sb.toString()
    }

    internal fun importMap(data: String): LinkedHashMap<String, Any> {
        val out = LinkedHashMap<String, Any>()
        data.lineSequence().forEach { raw ->
            if (raw.isBlank()) return@forEach
            val parts = raw.split(SEP, limit = 3)
            if (parts.size != 3) return@forEach
            val key = try {
                decode(parts[1])
            } catch (e: Exception) {
                return@forEach
            }
            if (key.isEmpty() || isSkippedKey(key)) return@forEach
            val value = parts[2]
            try {
                when (parts[0]) {
                    "s" -> out[key] = decode(value)
                    "b" -> out[key] = value == "1"
                    "i" -> out[key] = value.toInt()
                    "l" -> out[key] = value.toLong()
                    "f" -> out[key] = value.toFloat()
                    "t" -> out[key] =
                        if (value.isEmpty()) emptySet()
                        else value.split(",").mapTo(mutableSetOf()) { decode(it) }
                }
            } catch (e: Exception) {
                // Baris rusak dilewati; sisa cadangan tetap dipulihkan.
            }
        }
        return out
    }

    private fun appendLine(sb: StringBuilder, type: String, encodedKey: String, value: String) {
        sb.append(type).append(SEP).append(encodedKey).append(SEP).append(value).append('\n')
    }

    private fun isSkippedKey(key: String): Boolean =
        key.startsWith(SKIP_PREFIX) || key == SKIP_KEY_MIGRATION

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun decode(value: String): String = URLDecoder.decode(value, "UTF-8")
}
