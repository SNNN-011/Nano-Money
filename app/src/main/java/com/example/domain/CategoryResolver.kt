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
 *
 * Salah ketik ringan pada nama kategori ("makanana") juga ikut dicocokkan, karena
 * model yang jawabannya keliru satu huruf tidak layak membuat kategori baru.
 */
object CategoryResolver {

    private const val TOKEN_OVERLAP = 55
    private const val MIN_SCORE = 30

    private val NON_ALNUM = Regex("[^a-z0-9]+")

    /**
     * Nama kategori penampung untuk jawaban AI yang tidak cocok dengan kategori user.
     *
     * Nama yang sama juga dipakai saat kategori dihapus lalu transaksinya dipindahkan,
     * jadi cukup satu nama penanda di seluruh aplikasi.
     */
    const val CATCH_ALL_NAME = "Lainnya"

    /** Jarak huruf maksimum untuk menganggap sebuah kategori cuma salah ketik. */
    private const val TYPO_MAX_DISTANCE = 1

    /** Panjang minimum nama kategori sebelum typo matching dipakai. */
    private const val TYPO_MIN_LENGTH = 5

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

        // 4. Salah ketik ringan ("makanana" -> "Makanan"). Hanya untuk kata yang
        //    cukup panjang: pada kata pendek, beda satu huruf biasanya beda arti
        //    ("Ojek" vs "Ojol") dan menebaknya memindahkan transaksi ke kategori
        //    yang salah tanpa user sadari.
        //    ponytail: jarak 1 huruf & panjang minimal 5. Kalau nanti muncul kasus
        //    nyata yang beda 2 huruf, tambahkan dari data, bukan dari tebakan.
        if (cleaned.length >= TYPO_MIN_LENGTH) {
            normalizedAvailable
                .filter { it.second.length >= TYPO_MIN_LENGTH }
                .map { it.first to levenshtein(cleaned, it.second) }
                .filter { it.second <= TYPO_MAX_DISTANCE }
                .minByOrNull { it.second }
                ?.let { return CategoryResolution.Matched(it.first) }
        }

        return CategoryResolution.NeedsNewCategory(cleaned)
    }

    /**
     * Kategori akhir untuk satu jawaban AI.
     *
     * Kartu konfirmasi dan jalur simpan wajib memakai fungsi ini, bukan salinan
     * aturannya masing-masing: kalau keduanya berbeda, user menyetujui satu kategori
     * di kartu lalu yang tersimpan kategori lain.
     *
     * Jawaban yang tidak cocok tidak pernah dikarang jadi kategori baru, melainkan
     * masuk kategori penampung milik user. Kalau kategori penampungnya sudah dihapus,
     * namanya tetap dikembalikan apa adanya -- sama seperti perilaku lama.
     */
    fun finalCategory(aiCategory: String?, availableCategories: List<String>): String {
        val hasil = resolve(aiCategory, availableCategories)
        if (hasil is CategoryResolution.Matched) return hasil.category

        val penampung = normalize(CATCH_ALL_NAME)
        return availableCategories.firstOrNull { normalize(it) == penampung } ?: CATCH_ALL_NAME
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

    /**
     * Benar kalau [text] memuat salah satu [words] sebagai kata utuh.
     *
     * Dipakai untuk membaca kalimat user sendiri (mis. kata kerja "terima",
     * "bayar"), bukan jawaban AI. Pencocokan kata utuh penting di sini: tanpa itu
     * "dibayar" ikut terhitung sebagai "bayar" yang arahnya berlawanan.
     */
    fun containsAnyWord(text: String?, words: List<String>): Boolean {
        if (text.isNullOrBlank()) return false
        val haystack = normalize(text)
        if (haystack.isEmpty()) return false
        return words.any { containsWholeWord(haystack, it) }
    }

    /**
     * Benar kalau [text] menyebut salah satu nama kategori di [categories].
     *
     * Dipakai sebagai bukti arah uang: kalimat "terima gaji" menyebut kategori
     * "Gaji" milik user, sedangkan "bayar gaji karyawan" juga menyebutnya. Yang
     * membedakan arahnya adalah kata kerjanya, bukan kategori ini.
     */
    fun mentionsCategory(text: String?, categories: List<String>): Boolean {
        if (text.isNullOrBlank()) return false
        val haystack = normalize(text)
        if (haystack.isEmpty()) return false
        return categories.any { kategori ->
            val norm = normalize(kategori)
            norm.isNotEmpty() && containsWholeWord(haystack, norm)
        }
    }

    /**
     * Nama kategori dari [categories] yang benar-benar disebut di [text].
     *
     * Beda dengan [mentionsCategory] yang cuma menjawab ya/tidak: di sini nama
     * kategorinya dikembalikan supaya bisa dipakai sebagai pilihan akhir.
     * Dipakai untuk kategori buatan user yang sering tidak dikenali model
     * ("servis rem motor" -> "Servis Motor", "makanan kucing" -> "Kucing").
     *
     * Semua kata nama kategori harus muncul sebagai kata utuh. Urutan kata
     * bebas, jadi "Servis Motor" tetap cocok dengan "servis rem motor".
     * Kategori yang lebih panjang menang supaya "Kopi Susu" tidak kalah oleh
     * "Kopi" kalau keduanya ada.
     */
    fun categoryMentionedIn(text: String?, categories: List<String>): String? {
        if (text.isNullOrBlank()) return null
        val tokens = normalize(text).split(NON_ALNUM).filter { it.isNotBlank() }.toSet()
        if (tokens.isEmpty()) return null
        return categories
            .map { it to normalize(it).split(NON_ALNUM).filter { part -> part.length >= 2 } }
            .filter { (_, parts) -> parts.isNotEmpty() && parts.all { it in tokens } }
            .maxByOrNull { (_, parts) -> parts.sumOf { it.length } }
            ?.first
    }

    /** Jarak edit Levenshtein antara [a] dan [b] (jumlah huruf yang perlu diubah). */
    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            val tmp = prev
            prev = cur
            cur = tmp
        }
        return prev[b.length]
    }
}
