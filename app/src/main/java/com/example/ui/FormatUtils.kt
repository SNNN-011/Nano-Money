package com.example.ui

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
