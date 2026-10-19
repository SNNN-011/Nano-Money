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
 * Yang dibaca sengaja hanya nominal yang TIDAK ambigu:
 * 1. angka + satuan: "25 ribu", "1,5 juta", "100rb"
 * 2. angka bertanda rupiah: "Rp 15000", "Rp2.500.000"
 * 3. angka bertitik ribuan: "2.500.000", "1.500"
 *
 * Angka telanjang ("tanggal 28", "nasi goreng 15000") tidak dianggap nominal
 * supaya nomor tanggal/jam tidak tersedot jadi jumlah uang. Untuk kalimat
 * seperti itu jawaban model yang dipakai -- dan kalau model pun tidak
 * menyebut angka, aplikasi bertanya.
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

    /** Nominal pertama yang ditulis jelas, atau null kalau tidak ada. */
    fun fromText(text: String): Long? =
        fromUnit(text) ?: fromRupiah(text) ?: fromThousands(text)

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
