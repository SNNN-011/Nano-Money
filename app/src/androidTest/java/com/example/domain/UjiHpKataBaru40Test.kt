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
 * Uji 40 kalimat baru untuk kata sehari-hari yang ditambahkan 29 Okt 2026.
 *
 * Kalimat ditulis sendiri, tidak diambil dari `gold_v6/eval_599.jsonl` yang
 * dipakai memilih kata. Tujuannya mengukur apakah kenaikan 90,4% di eval ikut
 * terasa pada kalimat baru, bukan cuma hafalan pola eval.
 *
 * Jalankan:
 *   adb shell am instrument -w -e class com.example.domain.UjiHpKataBaru40Test \
 *     com.aistudio.pencatatkeuangan.nnomny.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class UjiHpKataBaru40Test {

    private companion object {
        const val TAG = "UJI40"

        val KATEGORI_MASUK = listOf(
            "Gaji", "Investasi", "Freelance", "Lainnya"
        )
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
        Kasus(1, "gajian tanggal 25 8,5 juta", MASUK, 8_500_000L, "Gaji"),
        Kasus(2, "dapat thr 4 juta", MASUK, 4_000_000L, "Gaji"),
        Kasus(3, "honor ngisi webinar 750rb", MASUK, 750_000L, "Gaji"),
        Kasus(4, "uang lembur bulan ini 450rb", MASUK, 450_000L, "Gaji"),
        Kasus(5, "orderan catering 1,2 juta", MASUK, 1_200_000L, "Freelance"),
        Kasus(6, "jualan baju di marketplace 900rb", MASUK, 900_000L, "Freelance"),
        Kasus(7, "dividen reksadana 220rb", MASUK, 220_000L, "Investasi"),
        Kasus(8, "bunga deposito 180rb", MASUK, 180_000L, "Investasi"),
        Kasus(9, "dapat hibah dari kampus 2 juta", MASUK, 2_000_000L, "Lainnya"),
        Kasus(10, "cashback shopee 45rb", MASUK, 45_000L, "Lainnya")
    )

    private val pengeluaran = listOf(
        Kasus(11, "sarapan bubur ayam 15rb", KELUAR, 15_000L, "Makanan"),
        Kasus(12, "pesan gofood 32rb", KELUAR, 32_000L, "Makanan"),
        Kasus(13, "jajan camilan di kantin 10rb", KELUAR, 10_000L, "Makanan"),
        Kasus(14, "beli kopi susu 18rb", KELUAR, 18_000L, "Makanan"),
        Kasus(15, "makan malam di resto 75rb", KELUAR, 75_000L, "Makanan"),
        Kasus(16, "isi pertalite 50rb", KELUAR, 50_000L, "Transportasi"),
        Kasus(17, "ongkos ojol ke kantor 12rb", KELUAR, 12_000L, "Transportasi"),
        Kasus(18, "bayar parkir 5rb", KELUAR, 5_000L, "Transportasi"),
        Kasus(19, "ongkir j&t 20rb", KELUAR, 20_000L, "Transportasi"),
        Kasus(20, "tambal ban motor 25rb", KELUAR, 25_000L, "Transportasi"),
        Kasus(21, "bayar kosan 850rb", KELUAR, 850_000L, "Tagihan"),
        Kasus(22, "beli paket data 50rb", KELUAR, 50_000L, "Tagihan"),
        Kasus(23, "bayar cicilan motor 1,2 juta", KELUAR, 1_200_000L, "Tagihan"),
        Kasus(24, "token listrik 100rb", KELUAR, 100_000L, "Tagihan"),
        Kasus(25, "iuran sampah 30rb", KELUAR, 30_000L, "Tagihan"),
        Kasus(26, "nonton bioskop 45rb", KELUAR, 45_000L, "Hiburan"),
        Kasus(27, "rekreasi ke ancol 150rb", KELUAR, 150_000L, "Hiburan"),
        Kasus(28, "karaoke bareng teman 80rb", KELUAR, 80_000L, "Hiburan"),
        Kasus(29, "beli beras 5kg 70rb", KELUAR, 70_000L, "Belanja"),
        Kasus(30, "beli skincare 120rb", KELUAR, 120_000L, "Belanja"),
        Kasus(31, "beli pasta gigi 18rb", KELUAR, 18_000L, "Belanja"),
        Kasus(32, "beli susu bayi 95rb", KELUAR, 95_000L, "Belanja"),
        Kasus(33, "beli vitamin c 60rb", KELUAR, 60_000L, "Kesehatan"),
        Kasus(34, "cek darah di lab 250rb", KELUAR, 250_000L, "Kesehatan"),
        Kasus(35, "periksa gigi 300rb", KELUAR, 300_000L, "Kesehatan"),
        Kasus(36, "beli masker 25rb", KELUAR, 25_000L, "Kesehatan"),
        Kasus(37, "bayar spp sekolah 600rb", KELUAR, 600_000L, "Tagihan"),
        Kasus(38, "bayar denda tilang 200rb", KELUAR, 200_000L, "Tagihan"),
        Kasus(39, "beli mainan anak 85rb", KELUAR, 85_000L, "Belanja"),
        Kasus(40, "beli popok bayi 65rb", KELUAR, 65_000L, "Belanja")
    )

    @Test
    fun jalankan40KalimatBaru() = runBlocking {
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
            println("UJI40|$baris")
        }

        val ringkas = "RINGKASAN dicatat=$dicatat dari 40 | " +
            "jenis=$jenisBenar nominal=$nominalBenar kategori=$kategoriBenar | gagal=$gagal"
        Log.i(TAG, ringkas)
        println("UJI40|$ringkas")
        meleset.forEach {
            Log.i(TAG, "MELESET|$it")
            println("UJI40|MELESET|$it")
        }

        assertEquals("ada kalimat yang gagal dijawab mesin lokal", 0, gagal)
        assertTrue("terlalu sedikit kalimat dicatat ($dicatat)", dicatat >= 36)
    }

    private fun tanda(ok: Boolean): String = if (ok) "OK" else "SALAH"
}
