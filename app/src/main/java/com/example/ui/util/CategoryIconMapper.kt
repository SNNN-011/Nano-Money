package com.example.ui.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.Locale

object CategoryIconMapper {
    fun extractEmojiAndLabel(input: String): Pair<String?, String> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return Pair(null, "")
        val codePoint = trimmed.codePointAt(0)
        val isEmoji = (codePoint in 0x1F300..0x1F9FF) || 
                      (codePoint in 0x1F600..0x1F64F) || 
                      (codePoint in 0x1F680..0x1F6FF) || 
                      (codePoint in 0x2600..0x27BF) || 
                      (codePoint in 0x1F1E6..0x1F1FF) ||
                      (codePoint in 0x1F900..0x1F9FF) ||
                      (codePoint in 0x1FA00..0x1FAFF)
                      
        if (isEmoji) {
            val emojiStr = String(Character.toChars(codePoint))
            val remaining = trimmed.substring(emojiStr.length).trim()
            return Pair(emojiStr, remaining)
        }
        return Pair(null, trimmed)
    }

    fun getIcon(category: String): ImageVector {
        val (_, cleanCategory) = extractEmojiAndLabel(category)
        return when (cleanCategory.lowercase(Locale.getDefault()).trim()) {
            "makanan", "kuliner", "makan", "food", "restaurant" -> Icons.Default.Restaurant
            "transportasi", "transport", "travel", "grab", "gojek", "mobil", "motor" -> Icons.Default.DirectionsCar
            "gaji", "bonus", "gaji bulanan", "pendapatan", "income", "payments" -> Icons.Default.Payments
            "belanja", "shopping", "keperluan" -> Icons.Default.ShoppingBag
            "utilitas", "tagihan", "listrik", "air", "bills" -> Icons.AutoMirrored.Filled.ReceiptLong
            "hiburan", "entertainment", "rekreasi", "nonton", "game" -> Icons.Default.TheaterComedy
            "kesehatan", "medis" -> Icons.Default.MedicalServices
            "investasi" -> Icons.AutoMirrored.Filled.TrendingUp
            "freelance", "pekerjaan" -> Icons.Default.Work
            else -> Icons.Default.Category
        }
    }
}
