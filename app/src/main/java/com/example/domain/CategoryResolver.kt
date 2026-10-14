package com.example.domain

import com.example.ui.util.CategoryIconMapper

/**
 * Hasil pencocokan kategori dari jawaban AI terhadap daftar kategori milik user.
 */
sealed interface CategoryResolution {
    /** Ketemu kategori yang benar-benar ada milik user. */
    data class Matched(val category: String) : CategoryResolution

    /** Tidak ada yang cocok; kategori ini perlu dibuat baru. */
    data class NeedsNewCategory(val suggestedName: String) : CategoryResolution
}

/**
 * Menerjemahkan kategori balasan AI menjadi kategori yang benar-benar ada.
 *
 * AI sering membalas kategori generik yang dia kenal dari dunia ("Hiburan",
 * "Makanan") padahal user sudah punya kategori khusus sendiri ("Game", "Kopi").
 * Karena itu jawaban AI tidak dipakai mentah: kalau ada kategori user yang mirip,
 * kategori itu yang dipilih. Ini tidak bisa dicegah hanya lewat prompt, karena
 * model tetap cenderung ke label umum.
 */
object CategoryResolver {

    private const val TOKEN_OVERLAP = 55
    private const val MIN_SCORE = 30

    fun resolve(
        aiCategory: String?,
        availableCategories: List<String>
    ): CategoryResolution {
        val cleaned = normalize(aiCategory)

        // Tanpa kategori dari AI, biarkan pemanggil yang memutuskan.
        if (cleaned.isEmpty()) return CategoryResolution.NeedsNewCategory("")

        val normalizedAvailable = availableCategories.map { it to normalize(it) }

        // 1. Cocok persis.
        normalizedAvailable.firstOrNull { it.second == cleaned }?.let {
            return CategoryResolution.Matched(it.first)
        }

        // 2. Satu sisi memuat sisi lain ("kopi" vs "kopi susu"), dan hanya dihitung
        //    kalau yang pendek muncul sebagai kata utuh. Tanpa syarat kata utuh,
        //    "Hiburan" ikut cocok dengan kategori "Hobi".
        normalizedAvailable.firstOrNull { (_, norm) ->
            norm.isNotEmpty() && cleaned.isNotEmpty() &&
                (
                    (cleaned.contains(norm) && containsWholeWord(cleaned, norm)) ||
                        (norm.contains(cleaned) && containsWholeWord(norm, cleaned))
                    )
        }?.let { return CategoryResolution.Matched(it.first) }

        // 3. Kata yang sama-sama muncul.
        val aiTokens = cleaned.split(' ').filter { it.isNotBlank() }.toSet()
        var best: Pair<String, Int>? = null
        normalizedAvailable.forEach { (raw, norm) ->
            if (norm.isEmpty()) return@forEach
            val tokens = norm.split(' ').filter { it.isNotBlank() }
            if (tokens.isEmpty()) return@forEach
            val hits = tokens.count { it in aiTokens }
            if (hits == 0) return@forEach
            val score = (hits * TOKEN_OVERLAP) / tokens.size
            if (score >= MIN_SCORE && (best == null || score > best!!.second)) {
                best = raw to score
            }
        }
        best?.let { return CategoryResolution.Matched(it.first) }

        return CategoryResolution.NeedsNewCategory(cleaned)
    }

    /** Buang emoji dan spasi berlebih, lalu huruf kecil semua. */
    fun normalize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val (_, clean) = CategoryIconMapper.extractEmojiAndLabel(raw.trim())
        return (clean.ifBlank { raw }).trim().lowercase().replace(Regex("\\s+"), " ")
    }

    /** [needle] muncul sebagai kata utuh di dalam [haystack]. */
    private fun containsWholeWord(haystack: String, needle: String): Boolean {
        if (needle.isEmpty()) return false
        val pattern = Regex("(^|[^a-z0-9])${Regex.escape(needle)}([^a-z0-9]|$)")
        return pattern.containsMatchIn(haystack)
    }
}
