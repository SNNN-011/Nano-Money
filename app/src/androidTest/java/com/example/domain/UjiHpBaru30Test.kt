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
 * Uji 30 kalimat BARU (tidak ada yang sama dengan [UjiHpDeviceTest]) lewat
 * jalur aplikasi sungguhan di HP: model dimuat dari aset APK, dijalankan mesin
 * native di perangkat.
 *
 * Fokus ronde ini: kata kerja yang berbeda-beda, frasa uang masuk yang tidak
 * memakai kata "terima", kalimat yang menyebut nama kategori arah lawan
 * ("dapat tagihan listrik"), dan satu kalimat dengan dua nominal.
 *
 * Jalankan:
 *   adb shell am instrument -w -e class com.example.domain.UjiHpBaru30Test \
 *     com.aistudio.pencatatkeuangan.nnomny.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class UjiHpBaru30Test {

    private companion object {
        const val TAG = "UJIBARU30"

        val KATEGORI_MASUK = listOf("Gaji", "Investasi", "Freelance", "Lainnya")
        val KATEGORI_KELUAR = listOf("Makanan", "Transportasi", "Tagihan", "Hiburan", "Belanja", "Lainnya")

        const val KELUAR = "PENGELUARAN"
        const val MASUK = "PEMASUKAN"
    }

    private data class Kasus(
        val no: Int,
        val grup: String,
        val teks: String,
        val jenis: String? = null,
        val nominal: Long? = null,
        val item: List<String>? = null,
        val harusTanya: Boolean = false,
        val harusTolak: Boolean = false
    )

    private val kasus = listOf(
        // A. pengeluaran dengan kata kerja yang berbeda
        Kasus(1, "A", "makan siang di warteg 20rb", KELUAR, 20_000L, listOf("warteg", "makan")),
        Kasus(2, "A", "beli galon air 22 ribu", KELUAR, 22_000L, listOf("galon")),
        Kasus(3, "A", "parkir motor 5rb", KELUAR, 5_000L, listOf("parkir")),
        Kasus(4, "A", "servis motor 150 ribu", KELUAR, 150_000L, listOf("servis", "motor")),
        Kasus(5, "A", "bayar kontrakan 1,8 juta", KELUAR, 1_800_000L, listOf("kontrakan")),
        Kasus(6, "A", "beli pulsa 15rb", KELUAR, 15_000L, listOf("pulsa")),
        Kasus(7, "A", "cuci motor 12 ribu", KELUAR, 12_000L, listOf("cuci", "motor")),
        Kasus(8, "A", "traktir teman makan 85rb", KELUAR, 85_000L, listOf("makan", "teman")),
        // B. uang masuk tanpa kata "terima"
        Kasus(9, "B", "gaji masuk 6,5 juta", MASUK, 6_500_000L, listOf("gaji")),
        Kasus(10, "B", "dapet komisi 750rb", MASUK, 750_000L, listOf("komisi")),
        Kasus(11, "B", "jual motor 8 juta", MASUK, 8_000_000L, listOf("motor")),
        Kasus(12, "B", "dapat kiriman dari kakak 1 juta", MASUK, 1_000_000L, null),
        Kasus(13, "B", "bunga tabungan 45 ribu", MASUK, 45_000L, listOf("bunga", "tabungan")),
        Kasus(14, "B", "proyek foto dibayar 2,5 juta", MASUK, 2_500_000L, listOf("foto", "proyek")),
        // C. nominal tidak disebut -> wajib bertanya
        Kasus(15, "C", "bayar kos bulan ini", harusTanya = true),
        Kasus(16, "C", "beli susu anak", harusTanya = true),
        Kasus(17, "C", "isi bensin motor", harusTanya = true),
        Kasus(18, "C", "bayar tagihan air", harusTanya = true),
        Kasus(19, "C", "beli kado ultah", harusTanya = true),
        Kasus(20, "C", "servis ac kamar", harusTanya = true),
        // D. jenis ambigu -> wajib bertanya
        Kasus(21, "D", "50 ribu", harusTanya = true),
        Kasus(22, "D", "ada duit 750rb", harusTanya = true),
        Kasus(23, "D", "uang 2 juta", harusTanya = true),
        // E. bukan transaksi -> tolak
        Kasus(24, "E", "selamat pagi", harusTolak = true),
        Kasus(25, "E", "kamu siapa sih", harusTolak = true),
        Kasus(26, "E", "besok libur ya", harusTolak = true),
        Kasus(27, "E", "tolong bantu aku dong", harusTolak = true),
        // F. kalimat rumit
        Kasus(28, "F", "dapat tagihan listrik 200 ribu", KELUAR, 200_000L, listOf("listrik")),
        Kasus(29, "F", "uang kiriman dari ibu 750 ribu", MASUK, 750_000L, null),
        Kasus(30, "F", "kemarin jajan bakso 15rb sama es teh 5rb", KELUAR, 15_000L, listOf("bakso", "jajan"))
    )

    @Test
    fun jalankan30KalimatBaru() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val parser = LocalTransactionParser(NeedleLocalEngine(context))

        var jenisBenar = 0
        var itemBenar = 0
        var nominalBenar = 0
        var kategoriWajar = 0
        var dicatat = 0
        var tanyaBenar = 0
        var tanyaDiminta = 0
        var tolakBenar = 0
        var tolakDiminta = 0
        var gagal = 0

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

                r.error == "bukan_transaksi" -> {
                    tolakDiminta++
                    val ok = k.harusTolak
                    if (ok) tolakBenar++
                    "${k.grup}|${k.no}|${k.teks}|TOLAK|" +
                        if (ok) "BENAR" else "SALAH (harusnya ${harapan(k)})"
                }

                r.error != null -> {
                    tanyaDiminta++
                    val ok = k.harusTanya
                    if (ok) tanyaBenar++
                    "${k.grup}|${k.no}|${k.teks}|TANYA|${r.pertanyaan}|" +
                        if (ok) "BENAR" else "SALAH (harusnya ${harapan(k)})"
                }

                else -> {
                    dicatat++
                    val jenisOk = r.type == k.jenis
                    val nominalOk = r.amount == k.nominal
                    val itemOk = k.item == null ||
                        k.item.any { (r.description ?: "").contains(it, true) }
                    val kategoriTersedia =
                        if (r.type == MASUK) KATEGORI_MASUK else KATEGORI_KELUAR
                    val resolusi = CategoryResolver.resolve(r.category, kategoriTersedia)
                    val kategoriOk = resolusi is CategoryResolution.Matched &&
                        resolusi.category != "Lainnya"
                    if (jenisOk) jenisBenar++
                    if (nominalOk) nominalBenar++
                    if (itemOk) itemBenar++
                    if (kategoriOk) kategoriWajar++
                    "${k.grup}|${k.no}|${k.teks}|CATAT|${r.type}|${r.amount}|" +
                        "${r.description}|${r.category}->" +
                        "${(resolusi as? CategoryResolution.Matched)?.category ?: "BARU"}|" +
                        "jenis=${tanda(jenisOk)} nominal=${tanda(nominalOk)} " +
                        "item=${tanda(itemOk)} kategori=${tanda(kategoriOk)}"
                }
            }
            Log.i(TAG, baris)
            println("UJIBARU30|$baris")
        }

        val ringkas = "RINGKASAN dicatat=$dicatat dari ${kasus.size} kasus | " +
            "jenis=$jenisBenar nominal=$nominalBenar item=$itemBenar kategori=$kategoriWajar " +
            "(dari $dicatat yang dicatat) | bertanya benar=$tanyaBenar/$tanyaDiminta | " +
            "menolak benar=$tolakBenar/$tolakDiminta | gagal=$gagal"
        Log.i(TAG, ringkas)
        println("UJIBARU30|$ringkas")

        assertEquals("ada kalimat yang gagal dijawab mesin lokal", 0, gagal)
        assertTrue(
            "terlalu sedikit kalimat yang dicatat ($dicatat dari ${kasus.size})",
            dicatat >= 10
        )
    }

    private fun harapan(k: Kasus): String = when {
        k.harusTolak -> "ditolak"
        k.harusTanya -> "bertanya"
        else -> "dicatat ${k.jenis} ${k.nominal}"
    }

    private fun tanda(ok: Boolean): String = if (ok) "OK" else "SALAH"
}
