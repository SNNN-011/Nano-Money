package com.example.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Membaca nominal yang ditulis jelas di kalimat user.
 *
 * Kenapa perlu: model kecil yang jadi otak lokal salah baca satuan besar.
 * Sudah diukur: "sewa kos 2 juta" dijawab 2000, "bayar listrik 350 ribu"
 * dijawab 3500, "beli baju 250 ribu" dijawab 250. Angka itu tidak boleh
 * langsung disimpan, karena nominal salah = catatan keuangan user salah.
 * Kalimat user sendiri adalah sumber paling tepercaya untuk nominal, jadi
 * dibaca di sini dan dipakai menggantikan angka model.
 *
 * Yang dibaca:
 * 1. angka + satuan: "25 ribu", "1,5 juta", "100rb"
 * 2. angka bertanda rupiah: "Rp 15000", "Rp2.500.000"
 * 3. angka bertitik ribuan: "2.500.000", "1.500"
 * 4. angka telanjang: "nasi goreng 15000", "bayar bensin 50000"
 *
 * Angka telanjang dibaca paling akhir dan dengan penjaga, karena angka di chat
 * tidak selalu uang: "jam 1230", "tahun 2026", "hp 081234567890", "tanggal 28".
 * Penjaganya ada di [bareAmount]. Kalau tidak ada nominal yang lolos penjaga,
 * jawaban model yang dipakai -- dan kalau model pun tidak menyebut angka,
 * aplikasi bertanya.
 *
 * Urutannya penting: satuan diperiksa lebih dulu supaya "Rp 2,5 juta" tidak
 * terbaca "Rp 2".
 */
object AmountReader {

    /**
     * Satuan yang dipakai orang Indonesia saat menulis nominal.
     *
     * Daftar ini bukan tebakan: seluruhnya diambil dari cara penulisan di data
     * latih dan data ujian ("rebu", "rbuan", "rbu", "reibu", "reibuan", "jta",
     * "jutaan"). Satuan yang tidak dikenal akan membuat nominal tidak terbaca,
     * dan itu jauh lebih baik daripada terbaca salah.
     */
    private val UNITS = mapOf(
        "k" to BigDecimal(1_000),
        "rb" to BigDecimal(1_000),
        "rbu" to BigDecimal(1_000),
        "ribu" to BigDecimal(1_000),
        "rebu" to BigDecimal(1_000),
        "reibu" to BigDecimal(1_000),
        "rbuan" to BigDecimal(1_000),
        "reibuan" to BigDecimal(1_000),
        "jt" to BigDecimal(1_000_000),
        "jta" to BigDecimal(1_000_000),
        "juta" to BigDecimal(1_000_000),
        "jutaan" to BigDecimal(1_000_000),
        "miliar" to BigDecimal(1_000_000_000),
        "milyar" to BigDecimal(1_000_000_000)
    )

    /** Satuan terpanjang lebih dulu supaya "rbuan" tidak terbaca sebagai "rb". */
    private val UNIT_PATTERN = UNITS.keys
        .sortedByDescending { it.length }
        .joinToString("|")

    /** "25ribu", "100 rb", "1,5 juta", "45 rebu". */
    private val NUMBER_WITH_UNIT =
        Regex("(?i)(\\d{1,15}(?:[.,]\\d{1,3})?)\\s*($UNIT_PATTERN)\\b")

    /** "Rp15000", "rp 2.500.000". Pemisah ribuan di dalam angka dibuang. */
    private val NUMBER_WITH_RUPIAH =
        Regex("(?i)rp\\.?\\s*(\\d{1,3}(?:[.,]\\d{3})+|\\d{1,15})")

    /**
     * "2.500.000", "1.500", "50.000". Titik dengan tepat tiga digit di
     * belakangnya pasti pemisah ribuan, bukan desimal, jadi angkanya tidak
     * ambigu. Tanggal bertitik ("19.04.2026") tidak ikut terbaca karena
     * kelompoknya bukan tiga digit.
     */
    private val NUMBER_WITH_THOUSANDS =
        Regex("(?<![\\d.])(\\d{1,3}(?:\\.\\d{3})+)(?![\\d.])")

    /**
     * Angka telanjang: "nasi goreng 15000", "bayar bensin 50000".
     *
     * Batasnya ketat supaya tidak memotong bagian dari angka lain: angka tidak
     * boleh menempel pada digit atau titik di kiri ("19.04" bukan dua nominal).
     */
    private val NUMBER_BARE = Regex("(?<![\\d.])(\\d{1,15})(?![\\d])")

    /** Nominal terkecil yang masih masuk akal untuk uang pada angka telanjang. */
    private const val BARE_MIN_AMOUNT = 1_000L

    /**
     * Angka 4 digit di rentang ini jauh lebih mungkin tahun daripada uang.
     * "bayar 2026" ditolak, sedangkan "bayar 1500" tetap dibaca.
     */
    private const val YEAR_FROM = 1900L
    private const val YEAR_TO = 2100L

    /** Nomor HP/WA sepanjang ini dengan awalan 0/62 bukan nominal. */
    private const val PHONE_MIN_DIGITS = 10

    /**
     * Kata tepat sebelum angka yang menandakan angka itu bukan uang.
     *
     * Isinya diambil dari kasus nyata, bukan tebakan: tanggalan dan jam ("jam
     * 1230", "tanggal 1234") yang muncul di data uji, plus identitas yang
     * panjangnya mirip nominal (nomor HP, rekening, resi).
     */
    private val NON_MONEY_WORDS = setOf(
        "tanggal", "tgl", "jam", "pukul", "tahun", "thn", "bulan", "bln",
        "minggu", "hari", "hr", "hp", "telp", "telepon", "wa", "nomor", "no",
        "nik", "ktp", "rekening", "rek", "resi", "kode"
    )

    /** Kata terakhir sebelum angka, atau null kalau tidak ada. */
    private val WORD_BEFORE = Regex("([A-Za-z]+)[^A-Za-z]*$")

    /** Nominal pertama yang ditulis jelas, atau null kalau tidak ada. */
    fun fromText(text: String): Long? =
        fromUnit(text) ?: fromRupiah(text) ?: fromThousands(text) ?: fromBare(text)

    /**
     * Semua nominal yang terbaca, urut posisinya di teks.
     *
     * Dipakai untuk mengenali kalimat yang memuat lebih dari satu transaksi
     * (lihat [looksLikeMultiple]). Nominal yang sudah terbaca pembaca lain tidak
     * dihitung dua kali, jadi "Rp 2.500.000" tetap satu nominal.
     */
    fun fromTextAll(text: String): List<Long> {
        val hits = mutableListOf<Pair<Int, Long>>()
        val used = mutableListOf<IntRange>()

        fun add(range: IntRange, value: Long) {
            if (used.any { it.first <= range.last && range.first <= it.last }) return
            used += range
            hits += range.first to value
        }

        NUMBER_WITH_UNIT.findAll(text).forEach { match ->
            val factor = UNITS[match.groupValues[2].lowercase()] ?: return@forEach
            val value = decimal(match.groupValues[1]) ?: return@forEach
            add(match.range, value.multiply(factor).setScale(0, RoundingMode.HALF_UP).toLong())
        }
        NUMBER_WITH_RUPIAH.findAll(text).forEach { match ->
            val value = match.groupValues[1].replace(".", "").replace(",", "").toLongOrNull()
                ?: return@forEach
            add(match.range, value)
        }
        NUMBER_WITH_THOUSANDS.findAll(text).forEach { match ->
            val value = match.groupValues[1].replace(".", "").toLongOrNull() ?: return@forEach
            add(match.range, value)
        }
        NUMBER_BARE.findAll(text).forEach { match ->
            val value = bareAmount(text, match) ?: return@forEach
            add(match.range, value)
        }

        return hits.sortedBy { it.first }.map { it.second }
    }

    /**
     * Benar kalau kalimat user memuat lebih dari satu nominal.
     *
     * Model dilatih mencatat SATU transaksi per kalimat. Kalimat seperti
     * "gajian 5jt, beli es 5k" tetap tercatat sebagai satu transaksi, jadi kartu
     * konfirmasi memberi tahu user bahwa sisanya perlu dicatat sendiri.
     */
    fun looksLikeMultiple(text: String): Boolean = fromTextAll(text).size >= 2

    private fun fromUnit(text: String): Long? {
        val match = NUMBER_WITH_UNIT.find(text) ?: return null
        val factor = UNITS[match.groupValues[2].lowercase()] ?: return null
        val value = decimal(match.groupValues[1]) ?: return null
        return value.multiply(factor).setScale(0, RoundingMode.HALF_UP).toLong()
    }

    private fun fromRupiah(text: String): Long? {
        val match = NUMBER_WITH_RUPIAH.find(text) ?: return null
        // Di belakang "Rp", titik dan koma selalu pemisah ribuan.
        return match.groupValues[1].replace(".", "").replace(",", "").toLongOrNull()
    }

    private fun fromThousands(text: String): Long? {
        val match = NUMBER_WITH_THOUSANDS.find(text) ?: return null
        return match.groupValues[1].replace(".", "").toLongOrNull()
    }

    private fun fromBare(text: String): Long? =
        NUMBER_BARE.findAll(text).firstNotNullOfOrNull { bareAmount(text, it) }

    /**
     * Nilai angka telanjang, atau null kalau salah satu penjaga menolaknya.
     *
     * Urutan penjaganya sengaja dari yang paling murah: besar angka, rentang
     * tahun, panjangnya seperti nomor HP, lalu kata sebelumnya.
     */
    private fun bareAmount(text: String, match: MatchResult): Long? {
        val digits = match.groupValues[1]
        val value = digits.toLongOrNull() ?: return null

        if (value < BARE_MIN_AMOUNT) return null
        if (digits.length == 4 && value in YEAR_FROM..YEAR_TO) return null
        if (digits.length >= PHONE_MIN_DIGITS &&
            (digits.startsWith("0") || digits.startsWith("62"))
        ) {
            return null
        }
        return value.takeIf { !precededByNonMoneyWord(text, match.range.first) }
    }

    /** Kata tepat sebelum angka termasuk penanda tanggal/jam/identitas. */
    private fun precededByNonMoneyWord(text: String, start: Int): Boolean {
        if (start <= 0) return false
        val prefix = text.substring(0, start)
        val word = WORD_BEFORE.find(prefix)?.groupValues?.get(1) ?: return false
        return word.lowercase() in NON_MONEY_WORDS
    }

    /**
     * "1,5" -> 1,5 dan "1.500" -> 1500. Titik dipakai orang untuk dua hal
     * sekaligus (desimal "2.5 juta" dan ribuan "1.500rb"), jadi dipisahkan
     * dengan melihat jumlah digit di belakangnya.
     */
    private fun decimal(raw: String): BigDecimal? {
        val comma = raw.lastIndexOf(',')
        val dot = raw.lastIndexOf('.')
        val normalized = when {
            comma == -1 && dot == -1 -> raw
            comma > dot -> raw.replace(".", "").replace(',', '.')
            raw.length - dot - 1 == 3 -> raw.replace(".", "")
            else -> raw.replace(",", "")
        }
        return normalized.toBigDecimalOrNull()
    }
}
