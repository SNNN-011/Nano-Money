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
 * Uji kategori KUSTOM + kalimat yang tidak ada di data latih.
 *
 * Tujuan: melihat apakah otak lokal benar-benar memilih kategori milik user
 * (bukan kategori bawaan), dan apakah penunjuk kategori bawaan menimpa pilihan
 * user ketika nama kategorinya mirip kata kunci lama ("kopi", "bonus").
 *
 * Kalimat di bawah dicek tidak ada di data_app/finetune.jsonl sebelum ditulis.
 *
 * Jalankan:
 *   adb shell am instrument -w -e class com.example.domain.UjiHpKustomTest \
 *     com.aistudio.pencatatkeuangan.nnomny.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class UjiHpKustomTest {

    private companion object {
        const val TAG = "UJIKUSTOM"

        // Kategori bawaan + kategori buatan user.
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
        val grup: String,
        val teks: String,
        val jenis: String,
        val nominal: Long,
        val kategori: String
    )

    // K = kategori kustom yang tidak bentrok dengan kata kunci bawaan.
    // T = kategori kustom yang namanya bentrok dengan kata kunci bawaan.
    // U = kalimat umum baru (kategori bawaan).
    private val kasus = listOf(
        Kasus(1, "K", "beli whiskas sama pasir wangi buat kucing 85rb", KELUAR, 85_000L, "Kucing"),
        Kasus(2, "K", "bayar vaksin kucing di klinik hewan 350 ribu", KELUAR, 350_000L, "Kucing"),
        Kasus(3, "K", "donasi buat korban gempa 100rb", KELUAR, 100_000L, "Donasi"),
        Kasus(4, "K", "nyumbang pembangunan masjid 250 ribu", KELUAR, 250_000L, "Donasi"),
        Kasus(5, "K", "sedekah ke panti asuhan 75rb", KELUAR, 75_000L, "Donasi"),
        Kasus(6, "K", "ganti oli motor matic 65rb", KELUAR, 65_000L, "Servis Motor"),
        Kasus(7, "K", "servis rem motor depan 120 ribu", KELUAR, 120_000L, "Servis Motor"),
        Kasus(8, "K", "jualan online minggu ini masuk 2,4 juta", MASUK, 2_400_000L, "Jualan Online"),
        Kasus(9, "K", "omzet lapak online 1,7 juta", MASUK, 1_700_000L, "Jualan Online"),
        Kasus(10, "T", "beli kopi susu gula aren 22rb", KELUAR, 22_000L, "Kopi"),
        Kasus(11, "T", "beli kopi bubuk buat seminggu 68rb", KELUAR, 68_000L, "Kopi"),
        Kasus(12, "T", "dapat bonus proyek 1,5 juta", MASUK, 1_500_000L, "Bonus"),
        Kasus(13, "T", "komisi jualan online 320rb", MASUK, 320_000L, "Jualan Online"),
        Kasus(14, "U", "nraktir anak buah makan siang 180rb", KELUAR, 180_000L, "Makanan"),
        Kasus(15, "U", "beli obat batuk di apotek 35rb", KELUAR, 35_000L, "Belanja"),
        Kasus(16, "U", "bayar denda telat bayar kartu 45rb", KELUAR, 45_000L, "Tagihan"),
        Kasus(17, "U", "beli kado pernikahan sahabat 300rb", KELUAR, 300_000L, "Belanja"),
        Kasus(18, "U", "warisan dari almarhum kakek 25 juta", MASUK, 25_000_000L, "Lainnya"),
        Kasus(19, "U", "dapat hadiah undian 2 juta", MASUK, 2_000_000L, "Lainnya")
    )

    @Test
    fun jalankanKategoriKustom() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val parser = LocalTransactionParser(NeedleLocalEngine(context))

        var dicatat = 0
        var jenisBenar = 0
        var nominalBenar = 0
        var kategoriBenar = 0
        var gagal = 0
        val perGrup = linkedMapOf("K" to intArrayOf(0, 0), "T" to intArrayOf(0, 0), "U" to intArrayOf(0, 0))

        for (k in kasus) {
            val hasil = parser.parseTransaction(k.teks, null, KATEGORI_MASUK, KATEGORI_KELUAR)
            val r = (hasil as? RequestResult.Success)?.data
            val pesanGagal = (hasil as? RequestResult.Error)?.message

            val baris = when {
                pesanGagal != null -> {
                    gagal++
                    "${k.grup}|${k.no}|${k.teks}|GAGAL|$pesanGagal"
                }

                r == null -> {
                    gagal++
                    "${k.grup}|${k.no}|${k.teks}|GAGAL|balasan kosong"
                }

                r.error != null -> {
                    "${k.grup}|${k.no}|${k.teks}|${r.error.uppercase()}|${r.pertanyaan ?: ""}|" +
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
                    val sel = perGrup.getValue(k.grup)
                    sel[1]++
                    if (kategoriOk) sel[0]++
                    "${k.grup}|${k.no}|${k.teks}|CATAT|${r.type}|${r.amount}|" +
                        "${r.description}|${r.category}->$kategoriFinal|" +
                        "harap=${k.kategori}|jenis=${tanda(jenisOk)} " +
                        "nominal=${tanda(nominalOk)} kategori=${tanda(kategoriOk)}"
                }
            }
            Log.i(TAG, baris)
            println("UJIKUSTOM|$baris")
        }

        val ringkas = buildString {
            append("RINGKASAN dicatat=$dicatat dari ${kasus.size} | ")
            append("jenis=$jenisBenar nominal=$nominalBenar kategori=$kategoriBenar | ")
            perGrup.forEach { (g, v) -> append("$g=${v[0]}/${v[1]} ") }
            append("| gagal=$gagal")
        }
        Log.i(TAG, ringkas)
        println("UJIKUSTOM|$ringkas")

        assertEquals("ada kalimat yang gagal dijawab mesin lokal", 0, gagal)
        assertTrue("terlalu sedikit kalimat dicatat ($dicatat)", dicatat >= 15)
    }

    private fun tanda(ok: Boolean): String = if (ok) "OK" else "SALAH"
}
