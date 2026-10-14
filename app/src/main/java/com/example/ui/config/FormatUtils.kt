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
        if (amount >= 1_000_000_000) {
            val formatted = String.format(ID_LOCALE, "%.1f", amount / 1_000_000_000.0)
            return "Rp ${formatted.removeSuffix(".0")} M"
        }
        if (amount >= 1_000_000) {
            val formatted = String.format(ID_LOCALE, "%.1f", amount / 1_000_000.0)
            return "Rp ${formatted.removeSuffix(".0")} jt"
        }
        if (amount >= 1_000) {
            val formatted = String.format(ID_LOCALE, "%.1f", amount / 1_000.0)
            return "Rp ${formatted.removeSuffix(".0")} rb"
        }
        return formatRupiah(amount)
    }

    fun formatDate(timestamp: Long): String = dateFormat.get().format(Date(timestamp))

    /**
     * Format stempel waktu ISO dari Google Drive (mis. "2026-10-13T07:45:00.000Z").
     * String yang tidak bisa diparse dikembalikan apa adanya, bukan string kosong,
     * supaya daftar cadangan tidak pernah tampil tanpa tanggal tanpa penjelasan.
     */
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
