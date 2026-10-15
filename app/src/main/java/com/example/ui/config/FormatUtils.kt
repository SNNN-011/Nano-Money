package com.example.ui.config

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

object FormatUtils {

    private val ID_LOCALE: Locale = Locale.forLanguageTag("id-ID")

    /**
     * Formatter yang dipakai ulang, bukan dibuat per pemanggilan.
     *
     * NumberFormat.getCurrencyInstance() cukup mahal karena membangun symbol dan
     * pattern dari nol. Dipanggil dari dalam item daftar yang terus recomposition
     * saat scroll, jadi harus dibuat sekali saja.
     *
     * SimpleDateFormat tidak thread-safe, jadi satu instance per thread lewat ThreadLocal.
     */
    private val currencyFormat = ThreadLocal.withInitial {
        NumberFormat.getCurrencyInstance(ID_LOCALE)
    }

    private val numberFormat = ThreadLocal.withInitial {
        NumberFormat.getNumberInstance(ID_LOCALE)
    }

    private val dateFormat = ThreadLocal.withInitial {
        SimpleDateFormat("dd MMM yyyy", ID_LOCALE)
    }

    fun formatInputNumber(input: String): String {
        val cleanString = input.replace(".", "").replace(",", "")
        if (cleanString.isEmpty()) return ""
        val parsed = cleanString.toLongOrNull() ?: return input
        return numberFormat.get().format(parsed)
    }

    /**
     * Format number input while preserving cursor position.
     * Returns a TextFieldValue with the cursor placed after the same digit the user was editing.
     */
    fun formatInputNumberPreserveCursor(fieldValue: TextFieldValue): TextFieldValue {
        val oldText = fieldValue.text
        val cursorPos = fieldValue.selection.start.coerceIn(0, oldText.length)
        val digitsBeforeCursor = oldText.substring(0, cursorPos).count { it.isDigit() }
        val cleanInput = oldText.replace(".", "").replace(",", "")
        if (cleanInput.isEmpty()) return TextFieldValue("", TextRange(0))
        if (!cleanInput.all { it.isDigit() }) return fieldValue
        val formatted = formatInputNumber(cleanInput)
        var digitCount = 0
        var newCursorPos = formatted.length
        for (i in formatted.indices) {
            if (formatted[i].isDigit()) {
                digitCount++
                if (digitCount > digitsBeforeCursor) {
                    newCursorPos = i
                    break
                }
            }
            if (i == formatted.length - 1) newCursorPos = formatted.length
        }
        return TextFieldValue(formatted, TextRange(newCursorPos))
    }

    fun formatRupiah(amount: Long): String {
        return try {
            val formatted = currencyFormat.get().format(amount)
            if (formatted.startsWith("Rp")) {
                formatted.replace("Rp", "Rp ").substringBefore(",")
            } else {
                "Rp " + numberFormat.get().format(amount)
            }
        } catch (e: Exception) {
            "Rp " + numberFormat.get().format(amount)
        }
    }

    fun formatRupiahCompact(amount: Long): String {
        // Locale id-ID memakai koma sebagai pemisah desimal, jadi angka bulat
        // menghasilkan "5,0" — sisa ",0" (bukan ".0") yang harus dibuang supaya
        // kartu anggaran tidak menampilkan "Rp 5,0 jt". Keduanya tetap dibuang
        // untuk jaga-jaga bila data locale berubah.
        fun formatSatuan(value: Double, satuan: String): String {
            val formatted = String.format(ID_LOCALE, "%.1f", value)
                .removeSuffix(",0")
                .removeSuffix(".0")
            return "Rp $formatted $satuan"
        }
        if (amount >= 1_000_000_000) return formatSatuan(amount / 1_000_000_000.0, "M")
        if (amount >= 1_000_000) return formatSatuan(amount / 1_000_000.0, "jt")
        if (amount >= 1_000) return formatSatuan(amount / 1_000.0, "rb")
        return formatRupiah(amount)
    }

    fun formatDate(timestamp: Long): String = dateFormat.get().format(Date(timestamp))

    /**
     * Format stempel waktu ISO dari Google Drive (mis. "2026-10-13T07:45:00.000Z").
     * String yang tidak bisa diparse dikembalikan apa adanya, bukan string kosong,
     * supaya daftar cadangan tidak pernah tampil tanpa tanggal tanpa penjelasan.
     */
        /** Ukuran berkas dalam satuan yang enak dibaca. */
    fun formatFileSize(bytes: Long): String = when {
        bytes <= 0L -> "0 B"
        bytes < 1_024L -> "$bytes B"
        bytes < 1_048_576L -> String.format(ID_LOCALE, "%.0f KB", bytes / 1_024.0)
        bytes < 1_073_741_824L -> String.format(ID_LOCALE, "%.1f MB", bytes / 1_048_576.0)
        else -> String.format(ID_LOCALE, "%.2f GB", bytes / 1_073_741_824.0)
    }

    fun formatDriveTimestamp(iso: String?): String {
        if (iso.isNullOrBlank()) return "Waktu tidak diketahui"
        return try {
            val parsed = java.time.Instant.parse(iso)
            dateFormat.get().format(Date.from(parsed))
        } catch (e: Exception) {
            try {
                val parsed = java.time.OffsetDateTime.parse(iso).toInstant()
                dateFormat.get().format(Date.from(parsed))
            } catch (e2: Exception) {
                iso
            }
        }
    }
}
