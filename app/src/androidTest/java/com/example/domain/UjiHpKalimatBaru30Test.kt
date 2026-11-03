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
 * Uji 30 kalimat yang TIDAK ADA di data latihan.
 *
 * Diverifikasi 30 Okt 2026 terhadap 109.556 query dari 12 korpus
 * (data_app/train|finetune|test, data/train|finetune|test|validation,
 * draf_v5/train|finetune|test|validation, gold_v6/eval_599):
 * 0 cocok persis, 0 cocok setelah normalisasi, kemiripan token tertinggi 0,67.
 *
 * Bedanya dengan [UjiHpKataBaru40Test]: kalimat di sana dipakai saat memilih kata,
 * jadi bisa jadi penghafalan. Set ini tidak dipakai memilih apa pun.
 *
 * Jalankan:
 *   adb shell am instrument -w -e class com.example.domain.UjiHpKalimatBaru30Test \
 *     com.aistudio.pencatatkeuangan.nnomny.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class UjiHpKalimatBaru30Test {

    private companion object {
        const val TAG = "UJI30"

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
        Kasus(1, "dibayar klien desain logo 1,8 juta", MASUK, 1_800_000L, "Freelance"),
        Kasus(2, "komisi jualan kosmetik 320rb", MASUK, 320_000L, "Freelance"),
        Kasus(3, "gaji bulan kesebelas 7,4 juta", MASUK, 7_400_000L, "Gaji"),
        Kasus(4, "tunjangan transportasi 600rb", MASUK, 600_000L, "Gaji"),
        Kasus(5, "hasil panen sawah 2,3 juta", MASUK, 2_300_000L, "Lainnya"),
        Kasus(6, "bunga tabungan berjangka 95rb", MASUK, 95_000L, "Investasi"),
        Kasus(7, "dapat arisan 1,5 juta", MASUK, 1_500_000L, "Lainnya"),
        Kasus(8, "bayaran ngajar les privat 400rb", MASUK, 400_000L, "Freelance"),
        Kasus(9, "dividen saham bca 260rb", MASUK, 260_000L, "Investasi"),
        Kasus(10, "pesangon kontrak habis 12 juta", MASUK, 12_000_000L, "Gaji")
    )

    private val pengeluaran = listOf(
        Kasus(11, "naik transjakarta 3500", KELUAR, 3_500L, "Transportasi"),
        Kasus(12, "bayar tol cipali 87rb", KELUAR, 87_000L, "Transportasi"),
        Kasus(13, "servis motor matic 90rb", KELUAR, 90_000L, "Transportasi"),
        Kasus(14, "beli obat batuk sirup 32rb", KELUAR, 32_000L, "Kesehatan"),
        Kasus(15, "periksa mata di optik 150rb", KELUAR, 150_000L, "Kesehatan"),
        Kasus(16, "nonton konser dangdut 350rb", KELUAR, 350_000L, "Hiburan"),
        Kasus(17, "sewa sepeda listrik 20rb", KELUAR, 20_000L, "Transportasi"),
        Kasus(18, "beli sajadah baru 120rb", KELUAR, 120_000L, "Belanja"),
        Kasus(19, "bayar uang gedung sekolah 2 juta", KELUAR, 2_000_000L, "Tagihan"),
        Kasus(20, "makan siang di warteg 14rb", KELUAR, 14_000L, "Makanan"),
        Kasus(21, "beli cilok 5rb", KELUAR, 5_000L, "Makanan"),
        Kasus(22, "sedekah ke panti asuhan 150rb", KELUAR, 150_000L, "Lainnya"),
        Kasus(23, "beli sabun cuci piring 15rb", KELUAR, 15_000L, "Belanja"),
        Kasus(24, "isi token listrik 200rb", KELUAR, 200_000L, "Tagihan"),
        Kasus(25, "bayar wifi indihome 385rb", KELUAR, 385_000L, "Tagihan"),
        Kasus(26, "beli kado ultah teman 75rb", KELUAR, 75_000L, "Belanja"),
        Kasus(27, "tiket masuk kebun raya 25rb", KELUAR, 25_000L, "Hiburan"),
        Kasus(28, "beli madu hutan 85rb", KELUAR, 85_000L, "Kesehatan"),
        Kasus(29, "ongkos becak 10rb", KELUAR, 10_000L, "Transportasi"),
        Kasus(30, "beli pupuk tanaman 45rb", KELUAR, 45_000L, "Belanja")
    )

    @Test
    fun jalankan30KalimatBaru() = runBlocking {
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
            println("UJI30|$baris")
        }

        val ringkas = "RINGKASAN dicatat=$dicatat dari 30 | " +
            "jenis=$jenisBenar nominal=$nominalBenar kategori=$kategoriBenar | gagal=$gagal"
        Log.i(TAG, ringkas)
        println("UJI30|$ringkas")
        meleset.forEach {
            Log.i(TAG, "MELESET|$it")
            println("UJI30|MELESET|$it")
        }

        assertEquals("ada kalimat yang gagal dijawab mesin lokal", 0, gagal)
        assertTrue("terlalu sedikit kalimat dicatat ($dicatat)", dicatat >= 27)
    }

    private fun tanda(ok: Boolean): String = if (ok) "OK" else "SALAH"
}
