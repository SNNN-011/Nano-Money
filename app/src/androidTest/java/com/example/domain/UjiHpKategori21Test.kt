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
 * Uji 21 kategori bawaan baru pada perangkat, 2 kalimat per kategori.
 *
 * Kalimat ditulis sendiri, bukan diambil dari eval. Yang diuji: jenis, nominal,
 * dan kategori akhir setelah lapisan kata kunci + resolver.
 *
 * Jalankan:
 *   adb shell am instrument -w -e class com.example.domain.UjiHpKategori21Test \
 *     com.aistudio.pencatatkeuangan.nnomny.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class UjiHpKategori21Test {

    private companion object {
        const val TAG = "UJI21"

        val KATEGORI_MASUK = listOf(
            "Gaji", "Uang Saku", "Freelance", "Bisnis",
            "Investasi", "Bonus", "Hadiah", "Lainnya"
        )
        val KATEGORI_KELUAR = listOf(
            "Makanan & Minuman", "Transportasi", "Belanja", "Kebutuhan Rumah",
            "Tagihan & Utilitas", "Pendidikan", "Kesehatan", "Hiburan",
            "Langganan", "Internet & Pulsa", "Perawatan Diri", "Donasi", "Lainnya"
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
        Kasus(2, "terima gaji 6 juta", MASUK, 6_000_000L, "Gaji"),
        Kasus(3, "dapat uang saku 500rb dari ibu", MASUK, 500_000L, "Uang Saku"),
        Kasus(4, "dikasih uang jajan 200rb", MASUK, 200_000L, "Uang Saku"),
        Kasus(5, "dapat fee proyek desain 2 juta", MASUK, 2_000_000L, "Freelance"),
        Kasus(6, "dibayar uang jasa 800rb", MASUK, 800_000L, "Freelance"),
        Kasus(7, "dapat omzet dagangan 3 juta", MASUK, 3_000_000L, "Bisnis"),
        Kasus(8, "jualan online 1,2 juta", MASUK, 1_200_000L, "Bisnis"),
        Kasus(9, "dapat dividen saham 400rb", MASUK, 400_000L, "Investasi"),
        Kasus(10, "cair profit crypto 1 juta", MASUK, 1_000_000L, "Investasi"),
        Kasus(11, "dapat bonus tahunan 2 juta", MASUK, 2_000_000L, "Bonus"),
        Kasus(12, "terima thr 3 juta", MASUK, 3_000_000L, "Bonus"),
        Kasus(13, "dapat hadiah 500rb", MASUK, 500_000L, "Hadiah"),
        Kasus(14, "terima angpao 200rb", MASUK, 200_000L, "Hadiah"),
        Kasus(15, "dapat kiriman 1 juta", MASUK, 1_000_000L, "Lainnya"),
        Kasus(16, "dapat cashback shopee 45rb", MASUK, 45_000L, "Lainnya")
    )

    private val pengeluaran = listOf(
        Kasus(17, "sarapan bubur ayam 15rb", KELUAR, 15_000L, "Makanan & Minuman"),
        Kasus(18, "beli kopi susu 18rb", KELUAR, 18_000L, "Makanan & Minuman"),
        Kasus(19, "isi pertalite 50rb", KELUAR, 50_000L, "Transportasi"),
        Kasus(20, "bayar parkir 5rb", KELUAR, 5_000L, "Transportasi"),
        Kasus(21, "beli sepatu 300rb", KELUAR, 300_000L, "Belanja"),
        Kasus(22, "beli baju di shopee 150rb", KELUAR, 150_000L, "Belanja"),
        Kasus(23, "beli sabun 15rb", KELUAR, 15_000L, "Kebutuhan Rumah"),
        Kasus(24, "beli beras 5kg 70rb", KELUAR, 70_000L, "Kebutuhan Rumah"),
        Kasus(25, "bayar listrik 200rb", KELUAR, 200_000L, "Tagihan & Utilitas"),
        Kasus(26, "bayar kosan 850rb", KELUAR, 850_000L, "Tagihan & Utilitas"),
        Kasus(27, "bayar spp sekolah 600rb", KELUAR, 600_000L, "Pendidikan"),
        Kasus(28, "bayar ukt kuliah 2 juta", KELUAR, 2_000_000L, "Pendidikan"),
        Kasus(29, "beli obat 30rb", KELUAR, 30_000L, "Kesehatan"),
        Kasus(30, "bayar cek darah di lab 250rb", KELUAR, 250_000L, "Kesehatan"),
        Kasus(31, "beli tiket bioskop 45rb", KELUAR, 45_000L, "Hiburan"),
        Kasus(32, "bayar karaoke 80rb", KELUAR, 80_000L, "Hiburan"),
        Kasus(33, "bayar netflix 54rb", KELUAR, 54_000L, "Langganan"),
        Kasus(34, "beli spotify premium 50rb", KELUAR, 50_000L, "Langganan"),
        Kasus(35, "beli pulsa 25rb", KELUAR, 25_000L, "Internet & Pulsa"),
        Kasus(36, "bayar wifi bulanan 300rb", KELUAR, 300_000L, "Internet & Pulsa"),
        Kasus(37, "beli skincare 150rb", KELUAR, 150_000L, "Perawatan Diri"),
        Kasus(38, "bayar potong rambut 40rb", KELUAR, 40_000L, "Perawatan Diri"),
        Kasus(39, "sedekah 100rb", KELUAR, 100_000L, "Donasi"),
        Kasus(40, "bayar zakat 500rb", KELUAR, 500_000L, "Donasi"),
        Kasus(41, "bayar biaya admin bank 5rb", KELUAR, 5_000L, "Lainnya"),
        Kasus(42, "bayar biaya tak terduga 25rb", KELUAR, 25_000L, "Lainnya")
    )

    @Test
    fun jalankan42Kalimat() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val parser = LocalTransactionParser(NeedleLocalEngine(context))

        var jenisBenar = 0
        var nominalBenar = 0
        var kategoriBenar = 0
        var dicatat = 0
        var gagal = 0
        val meleset = mutableListOf<String>()

        for (k in pemasukan + pengeluaran) {
            val hasil = parser.parseTransaction(k.teks, null, KATEGORI_MASUK, KATEGORI_KELUAR)
            val r = (hasil as? RequestResult.Success)?.data
            val pesanGagal = (hasil as? RequestResult.Error)?.message

            val baris = when {
                pesanGagal != null -> {
                    gagal++
                    "${k.no}|${k.teks}|GAGAL|$pesanGagal"
                }

                r == null -> {
                    gagal++
                    "${k.no}|${k.teks}|GAGAL|balasan kosong"
                }

                r.error != null -> {
                    "${k.no}|${k.teks}|${r.error.uppercase()}|${r.pertanyaan ?: ""}|" +
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
                    "${k.no}|${k.teks}|CATAT|${r.type}|${r.amount}|" +
                        "${r.description}|$kategoriFinal|" +
                        "harap=${k.kategori}|jenis=${tanda(jenisOk)} " +
                        "nominal=${tanda(nominalOk)} kategori=${tanda(kategoriOk)}"
                }
            }
            Log.i(TAG, baris)
            println("UJI21|$baris")
        }

        val ringkas = "RINGKASAN dicatat=$dicatat dari 42 | " +
            "jenis=$jenisBenar nominal=$nominalBenar kategori=$kategoriBenar | gagal=$gagal"
        Log.i(TAG, ringkas)
        println("UJI21|$ringkas")
        meleset.forEach {
            Log.i(TAG, "MELESET|$it")
            println("UJI21|MELESET|$it")
        }

        assertEquals("ada kalimat yang gagal dijawab mesin lokal", 0, gagal)
        assertTrue("terlalu sedikit kalimat dicatat ($dicatat)", dicatat >= 40)
        assertTrue("jenis benar terlalu sedikit ($jenisBenar/42)", jenisBenar >= 41)
        assertTrue("kategori benar terlalu sedikit ($kategoriBenar/42)", kategoriBenar >= 40)
    }

    private fun tanda(ok: Boolean): String = if (ok) "OK" else "SALAH"
}
