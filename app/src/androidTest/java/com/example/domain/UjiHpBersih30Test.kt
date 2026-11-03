package com.example.domain

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.NeedleLocalEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Uji 30 kalimat baru, set KETIGA, untuk mengukur angka bersih setelah empat
 * akar aturan diperbaiki (2 Nov 2026).
 *
 * Beda dengan [UjiHpKalimatBaru30Test]: kalimat di sana dipakai MENDESAIN
 * perbaikan, jadi hasilnya tidak bebas bias. Set ini ditulis sesudah perbaikan
 * dan tidak dipakai memilih aturan apa pun.
 *
 * Diverifikasi: 0 cocok persis / setelah normalisasi terhadap 109.556 query dari
 * 12 korpus, dan kemiripan token tertinggi < 0,75 terhadap set uji lama
 * (30 kalimat + 40 kalimat).
 *
 * Jalankan:
 *   adb shell am instrument -e class com.example.domain.UjiHpBersih30Test \
 *     com.aistudio.pencatatkeuangan.nnomny.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class UjiHpBersih30Test {

    private companion object {
        const val TAG = "UJI30B"

        val KATEGORI_MASUK = listOf("Gaji", "Investasi", "Freelance", "Lainnya")
        val KATEGORI_KELUAR = listOf(
            "Makanan", "Transportasi", "Tagihan", "Hiburan", "Belanja",
            "Kesehatan", "Lainnya"
        )

        const val KELUAR = "PENGELUARAN"
        const val MASUK = "PEMASUKAN"
    }

    private data class Kasus(
        val no: Int,
        val teks: String,
        val jenis: String,
        val nominal: Long,
        val kategori: String
    )

    private val pemasukan = listOf(
        Kasus(1, "fee konsultan pajak 2,4 juta", MASUK, 2_400_000L, "Freelance"),
        Kasus(2, "uang pensiun bulan ini 3 juta", MASUK, 3_000_000L, "Gaji"),
        Kasus(3, "komisi afiliasi 780rb", MASUK, 780_000L, "Gaji"),
        Kasus(4, "dibayar proyek renovasi 5 juta", MASUK, 5_000_000L, "Freelance"),
        Kasus(5, "dapat bunga obligasi 210rb", MASUK, 210_000L, "Investasi"),
        Kasus(6, "dapat warisan 25 juta", MASUK, 25_000_000L, "Lainnya"),
        Kasus(7, "uang kasbon dari bos 500rb", MASUK, 500_000L, "Lainnya"),
        Kasus(8, "beasiswa bulanan 1,2 juta", MASUK, 1_200_000L, "Gaji"),
        Kasus(9, "dapat profit kripto 640rb", MASUK, 640_000L, "Investasi"),
        Kasus(10, "uang saku dari ibu 300rb", MASUK, 300_000L, "Lainnya")
    )

    private val pengeluaran = listOf(
        Kasus(11, "naik krl ke sudirman 4rb", KELUAR, 4_000L, "Transportasi"),
        Kasus(12, "beli bensin pertamax 60rb", KELUAR, 60_000L, "Transportasi"),
        Kasus(13, "tambal ban tubeless 35rb", KELUAR, 35_000L, "Transportasi"),
        Kasus(14, "bayar tol jorr 25rb", KELUAR, 25_000L, "Transportasi"),
        Kasus(15, "makan bakso malang 18rb", KELUAR, 18_000L, "Makanan"),
        Kasus(16, "beli martabak manis 45rb", KELUAR, 45_000L, "Makanan"),
        Kasus(17, "ngopi di kafe 28rb", KELUAR, 28_000L, "Makanan"),
        Kasus(18, "beli ayam geprek 22rb", KELUAR, 22_000L, "Makanan"),
        Kasus(19, "bayar listrik bulanan 240rb", KELUAR, 240_000L, "Tagihan"),
        Kasus(20, "cicilan hp 750rb", KELUAR, 750_000L, "Tagihan"),
        Kasus(21, "bayar iuran bpjs 150rb", KELUAR, 150_000L, "Tagihan"),
        Kasus(22, "beli pulsa telkomsel 25rb", KELUAR, 25_000L, "Tagihan"),
        Kasus(23, "nonton drakor di vidio 30rb", KELUAR, 30_000L, "Hiburan"),
        Kasus(24, "main bowling 90rb", KELUAR, 90_000L, "Hiburan"),
        Kasus(25, "beli jaket denim 230rb", KELUAR, 230_000L, "Belanja"),
        Kasus(26, "beli mukena 180rb", KELUAR, 180_000L, "Belanja"),
        Kasus(27, "beli vitamin d 55rb", KELUAR, 55_000L, "Kesehatan"),
        Kasus(28, "periksa ke dokter gigi 200rb", KELUAR, 200_000L, "Kesehatan"),
        Kasus(29, "beli popok bayi 68rb", KELUAR, 68_000L, "Belanja"),
        Kasus(30, "donasi korban banjir 100rb", KELUAR, 100_000L, "Lainnya")
    )

    @Test
    fun jalankan30KalimatBersih() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val parser = LocalTransactionParser(NeedleLocalEngine(context))

        var jenisBenar = 0
        var nominalBenar = 0
        var kategoriBenar = 0
        var dicatat = 0
        var gagal = 0
        val meleset = mutableListOf<String>()

        for (k in pemasukan + pengeluaran) {
            val grup = if (k.jenis == MASUK) "MASUK" else "KELUAR"
            val hasil = parser.parseTransaction(k.teks, null, KATEGORI_MASUK, KATEGORI_KELUAR)
            val r = (hasil as? RequestResult.Success)?.data
            val pesanGagal = (hasil as? RequestResult.Error)?.message

            val baris = when {
                pesanGagal != null -> {
                    gagal++
                    "$grup|${k.no}|${k.teks}|GAGAL|$pesanGagal"
                }

                r == null -> {
                    gagal++
                    "$grup|${k.no}|${k.teks}|GAGAL|balasan kosong"
                }

                r.error != null -> {
                    "$grup|${k.no}|${k.teks}|${r.error.uppercase()}|${r.pertanyaan ?: ""}|" +
                        "harusnya ${k.jenis} ${k.kategori}"
                }

                else -> {
                    dicatat++
                    val daftar = if (r.type == MASUK) KATEGORI_MASUK else KATEGORI_KELUAR
                    val kategoriFinal = CategoryResolver.finalCategory(r.category, daftar)
                    val jenisOk = r.type == k.jenis
                    val nominalOk = r.amount == k.nominal
                    val kategoriOk = kategoriFinal == k.kategori
                    if (jenisOk) jenisBenar++
                    if (nominalOk) nominalBenar++
                    if (kategoriOk) kategoriBenar++
                    if (!jenisOk || !nominalOk || !kategoriOk) {
                        meleset += "${k.no}|${k.teks}|" +
                            "dapat=${r.type}/${r.amount}/$kategoriFinal|" +
                            "harap=${k.jenis}/${k.nominal}/${k.kategori}"
                    }
                    "$grup|${k.no}|${k.teks}|CATAT|${r.type}|${r.amount}|" +
                        "${r.description}|$kategoriFinal|" +
                        "harap=${k.kategori}|jenis=${tanda(jenisOk)} " +
                        "nominal=${tanda(nominalOk)} kategori=${tanda(kategoriOk)}"
                }
            }
            Log.i(TAG, baris)
            println("UJI30B|$baris")
        }

        val ringkas = "RINGKASAN dicatat=$dicatat dari 30 | " +
            "jenis=$jenisBenar nominal=$nominalBenar kategori=$kategoriBenar | gagal=$gagal"
        Log.i(TAG, ringkas)
        println("UJI30B|$ringkas")
        meleset.forEach {
            Log.i(TAG, "MELESET|$it")
            println("UJI30B|MELESET|$it")
        }

        assertEquals("ada kalimat yang gagal dijawab mesin lokal", 0, gagal)
        assertTrue("terlalu sedikit kalimat dicatat ($dicatat)", dicatat >= 27)
    }

    private fun tanda(ok: Boolean): String = if (ok) "OK" else "SALAH"
}
