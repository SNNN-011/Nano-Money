package com.example.ui.config

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

object FormatUtils {
    fun formatInputNumber(input: String): String {
        val cleanString = input.replace(".", "").replace(",", "")
        if (cleanString.isEmpty()) return ""
        val parsed = cleanString.toLongOrNull() ?: return input
        return NumberFormat.getNumberInstance(Locale.forLanguageTag("id-ID")).format(parsed)
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

    fun formatRupiah(amount: Double): String {
        return try {
            val format = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("id-ID"))
            // Some locales might have other decimal separator layouts, let's ensure clean Indonesian styling
            val formatted = format.format(amount)
            if (formatted.startsWith("Rp")) {
                formatted.replace("Rp", "Rp ").substringBefore(",")
            } else {
                "Rp " + NumberFormat.getNumberInstance(Locale.forLanguageTag("id-ID")).format(amount)
            }
        } catch (e: Exception) {
            "Rp " + String.format(Locale.forLanguageTag("id-ID"), "%,.0f", amount)
        }
    }

    fun formatRupiahCompact(amount: Double): String {
        if (amount >= 1_000_000_000) {
            val formatted = String.format(Locale.forLanguageTag("id-ID"), "%.1f", amount / 1_000_000_000)
            return "Rp ${formatted.removeSuffix(".0")} M"
        }
        if (amount >= 1_000_000) {
            val formatted = String.format(Locale.forLanguageTag("id-ID"), "%.1f", amount / 1_000_000)
            return "Rp ${formatted.removeSuffix(".0")} jt"
        }
        if (amount >= 1_000) {
            val formatted = String.format(Locale.forLanguageTag("id-ID"), "%.1f", amount / 1_000)
            return "Rp ${formatted.removeSuffix(".0")} rb"
        }
        return formatRupiah(amount)
    }

    fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.forLanguageTag("id-ID"))
        return sdf.format(Date(timestamp))
    }
}
