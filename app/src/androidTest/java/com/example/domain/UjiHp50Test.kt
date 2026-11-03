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
 * Uji 50 kalimat baru (20 pemasukan, 30 pengeluaran) lewat jalur aplikasi di HP.
 *
 * Semua kalimat ditulis baru: tidak ada yang sama dengan [UjiHpDeviceTest],
 * [UjiHpBaru30Test], maupun [UjiHpKustomTest]. Tujuannya mengukur mutu otak r10
 * + aturan aplikasi pada kalimat yang belum pernah diuji, bukan menghafal ulang.
 *
 * Kategori yang dipakai sama dengan set kustom di [UjiHpKustomTest].
 *
 * Jalankan:
 *   adb shell am instrument -w -e class com.example.domain.UjiHp50Test \
 *     com.aistudio.pencatatkeuangan.nnomny.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class UjiHp50Test {

    private companion object {
        const val TAG = "UJI50"

        val KATEGORI_MASUK = listOf("Gaji", "Bonus", "Jualan Online", "Investasi", "Lainnya")
        val KATEGORI_KELUAR = listOf(
            "Makanan", "Transportasi", "Tagihan", "Hiburan", "Belanja",
            "Kopi", "Kucing", "Servis Motor", "Donasi", "Lainnya"
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
        Kasus(1, "gaji bulan oktober 7,2 juta", MASUK, 7_200_000L, "Gaji"),
        Kasus(2, "thr lebaran 3 juta", MASUK, 3_000_000L, "Gaji"),
        Kasus(3, "honor ngajar les privat 450rb", MASUK, 450_000L, "Gaji"),
        Kasus(4, "uang lembur proyek kantor 600rb", MASUK, 600_000L, "Gaji"),
        Kasus(5, "bonus akhir tahun 4,5 juta", MASUK, 4_500_000L, "Bonus"),
        Kasus(6, "bonus referral teman 250rb", MASUK, 250_000L, "Bonus"),
        Kasus(7, "omzet jualan di shopee 3,1 juta", MASUK, 3_100_000L, "Jualan Online"),
        Kasus(8, "lapak online dapat orderan 1,4 juta", MASUK, 1_400_000L, "Jualan Online"),
        Kasus(9, "dividen saham bca 320rb", MASUK, 320_000L, "Investasi"),
        Kasus(10, "bunga deposito 150 ribu", MASUK, 150_000L, "Investasi"),
        Kasus(11, "reksadana pasar uang cair 2 juta", MASUK, 2_000_000L, "Investasi"),
        Kasus(12, "warisan dari nenek 30 juta", MASUK, 30_000_000L, "Lainnya"),
        Kasus(13, "dapat hadiah lomba lari 1,5 juta", MASUK, 1_500_000L, "Lainnya"),
        Kasus(14, "dijual kamera bekas 2,3 juta", MASUK, 2_300_000L, "Lainnya"),
        Kasus(15, "uang kiriman dari ayah 2 juta", MASUK, 2_000_000L, "Lainnya"),
        Kasus(16, "cashback shopee 75rb", MASUK, 75_000L, "Lainnya"),
        Kasus(17, "refund tiket kereta 275rb", MASUK, 275_000L, "Lainnya"),
        Kasus(18, "uang ganti rugi asuransi 5 juta", MASUK, 5_000_000L, "Lainnya"),
        Kasus(19, "dapat uang dari jual sepeda 900rb", MASUK, 900_000L, "Lainnya"),
        Kasus(20, "penjualan marketplace masuk 1,2 juta", MASUK, 1_200_000L, "Jualan Online")
    )

    private val pengeluaran = listOf(
        Kasus(21, "makan siang padang 32rb", KELUAR, 32_000L, "Makanan"),
        Kasus(22, "beli mie ayam 15rb", KELUAR, 15_000L, "Makanan"),
        Kasus(23, "jajan martabak manis 40rb", KELUAR, 40_000L, "Makanan"),
        Kasus(24, "kopi susu kenangan 26rb", KELUAR, 26_000L, "Kopi"),
        Kasus(25, "beli kopi bubuk arabika 95rb", KELUAR, 95_000L, "Kopi"),
        Kasus(26, "beli whiskas dua kilo 120rb", KELUAR, 120_000L, "Kucing"),
        Kasus(27, "pasir kucing wangi 45rb", KELUAR, 45_000L, "Kucing"),
        Kasus(28, "bayar vaksin kucing 300rb", KELUAR, 300_000L, "Kucing"),
        Kasus(29, "isi bensin pertalite 60rb", KELUAR, 60_000L, "Transportasi"),
        Kasus(30, "tarif tol cipularang 65rb", KELUAR, 65_000L, "Transportasi"),
        Kasus(31, "grab ke kantor 28rb", KELUAR, 28_000L, "Transportasi"),
        Kasus(32, "parkir mall 5rb", KELUAR, 5_000L, "Transportasi"),
        Kasus(33, "cuci motor 15rb", KELUAR, 15_000L, "Transportasi"),
        Kasus(34, "ganti oli motor 70rb", KELUAR, 70_000L, "Servis Motor"),
        Kasus(35, "servis rem motor 130rb", KELUAR, 130_000L, "Servis Motor"),
        Kasus(36, "bayar listrik 420rb", KELUAR, 420_000L, "Tagihan"),
        Kasus(37, "isi pulsa 25rb", KELUAR, 25_000L, "Tagihan"),
        Kasus(38, "bayar wifi indihome 385rb", KELUAR, 385_000L, "Tagihan"),
        Kasus(39, "bayar kos bulan ini 1,8 juta", KELUAR, 1_800_000L, "Tagihan"),
        Kasus(40, "langganan netflix 54rb", KELUAR, 54_000L, "Hiburan"),
        Kasus(41, "nonton bioskop 50rb", KELUAR, 50_000L, "Hiburan"),
        Kasus(42, "topup game mobile legends 100rb", KELUAR, 100_000L, "Hiburan"),
        Kasus(43, "beli sepatu lari 750rb", KELUAR, 750_000L, "Belanja"),
        Kasus(44, "beli sabun dan sampo 85rb", KELUAR, 85_000L, "Belanja"),
        Kasus(45, "beli kado ulang tahun 200rb", KELUAR, 200_000L, "Belanja"),
        Kasus(46, "donasi bencana banjir 150rb", KELUAR, 150_000L, "Donasi"),
        Kasus(47, "sedekah jumat 50rb", KELUAR, 50_000L, "Donasi"),
        Kasus(48, "beli obat flu di apotek 28rb", KELUAR, 28_000L, "Belanja"),
        Kasus(49, "bayar denda parkir 100rb", KELUAR, 100_000L, "Tagihan"),
        Kasus(50, "beli galon air minum 22rb", KELUAR, 22_000L, "Tagihan")
    )

    @Test
    fun jalankan50KalimatBaru() = runBlocking {
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
            println("UJI50|$baris")
        }

        val ringkas = "RINGKASAN dicatat=$dicatat dari 50 | " +
            "jenis=$jenisBenar nominal=$nominalBenar kategori=$kategoriBenar | gagal=$gagal"
        Log.i(TAG, ringkas)
        println("UJI50|$ringkas")
        meleset.forEach {
            Log.i(TAG, "MELESET|$it")
            println("UJI50|MELESET|$it")
        }

        assertEquals("ada kalimat yang gagal dijawab mesin lokal", 0, gagal)
        assertTrue("terlalu sedikit kalimat dicatat ($dicatat)", dicatat >= 45)
    }

    private fun tanda(ok: Boolean): String = if (ok) "OK" else "SALAH"
}
