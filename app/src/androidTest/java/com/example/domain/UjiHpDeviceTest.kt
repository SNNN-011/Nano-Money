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
 * Uji 30 kalimat dari `UJI_HP.md` lewat jalur aplikasi sungguhan di HP.
 *
 * Bedanya dengan unit test JVM: di sini model benar-benar dimuat dari aset APK
 * dan dijalankan mesin native di perangkat, jadi yang terukur adalah yang akan
 * dilihat user di chat -- bukan tiruan.
 *
 * Keluarannya sengaja berupa baris teks berprefiks `UJI30|` supaya bisa dibaca
 * dari logcat maupun laporan test Gradle. Yang dihitung cuma 4 hal yang memang
 * jadi target: jenis, nama item, nominal, kategori.
 *
 * Jalankan:
 *   gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.domain.UjiHpDeviceTest
 */
@RunWith(AndroidJUnit4::class)
class UjiHpDeviceTest {

    private companion object {
        const val TAG = "UJI30"

        /** Kategori bawaan aplikasi (CategoryMigration). */
        val KATEGORI_MASUK = listOf("Gaji", "Investasi", "Freelance", "Lainnya")
        val KATEGORI_KELUAR = listOf("Makanan", "Transportasi", "Tagihan", "Hiburan", "Belanja", "Lainnya")

        const val KELUAR = "PENGELUARAN"
        const val MASUK = "PEMASUKAN"
    }

    /**
     * @param jenis jenis yang diharapkan, null kalau harus bertanya
     * @param item kata yang harus muncul di nama item; null = item boleh kosong
     */
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
        // A. pengeluaran dengan nominal jelas
        Kasus(1, "A", "beli bakso 25 ribu", KELUAR, 25_000L, listOf("bakso")),
        Kasus(2, "A", "nasi goreng 15000", KELUAR, 15_000L, listOf("goreng", "nasi")),
        Kasus(3, "A", "bayar listrik 350 ribu", KELUAR, 350_000L, listOf("listrik")),
        Kasus(4, "A", "isi bensin 100rb", KELUAR, 100_000L, listOf("bensin")),
        Kasus(5, "A", "belanja bulanan 1,5 juta", KELUAR, 1_500_000L, null),
        Kasus(6, "A", "topup gopay 50 ribu", KELUAR, 50_000L, listOf("gopay", "topup")),
        Kasus(7, "A", "jajan kopi 18rb", KELUAR, 18_000L, listOf("kopi")),
        Kasus(8, "A", "sewa kos 2 juta", KELUAR, 2_000_000L, listOf("kos", "sewa")),
        // B. uang masuk
        Kasus(9, "B", "terima gaji 5 juta", MASUK, 5_000_000L, listOf("gaji")),
        Kasus(10, "B", "dapat bonus 2 juta", MASUK, 2_000_000L, listOf("bonus")),
        Kasus(11, "B", "jual hp bekas 1,2 juta", MASUK, 1_200_000L, listOf("hp")),
        Kasus(12, "B", "cashback 35 ribu masuk", MASUK, 35_000L, listOf("cashback")),
        Kasus(13, "B", "dibayar klien 3 juta", MASUK, 3_000_000L, listOf("klien")),
        Kasus(14, "B", "uang kiriman ibu 500 ribu", MASUK, 500_000L, null),
        // C. nominal tidak disebut -> wajib bertanya
        Kasus(15, "C", "bayar iBox", harusTanya = true),
        Kasus(16, "C", "beli baju", harusTanya = true),
        Kasus(17, "C", "bayar kos", harusTanya = true),
        Kasus(18, "C", "ongkos ojek", harusTanya = true),
        Kasus(19, "C", "terima transfer", harusTanya = true),
        Kasus(20, "C", "beli obat di apotek", harusTanya = true),
        // D. jenis ambigu -> wajib bertanya
        Kasus(21, "D", "uang 200 ribu", harusTanya = true),
        Kasus(22, "D", "transfer 500 ribu", harusTanya = true),
        Kasus(23, "D", "ada 1 juta nih", harusTanya = true),
        // E. bukan transaksi -> tolak
        Kasus(24, "E", "apa kabar bot?", harusTolak = true),
        Kasus(25, "E", "cuaca hari ini bagus ya", harusTolak = true),
        Kasus(26, "E", "makasih ya", harusTolak = true),
        Kasus(27, "E", "cara hemat uang gimana?", harusTolak = true),
        // F. kalimat campur
        Kasus(28, "F", "bayar tagihan wifi 300rb sama beli pulsa 25 ribu", KELUAR, 300_000L, listOf("wifi", "tagihan")),
        Kasus(29, "F", "kemarin beli sepatu 800 ribu", KELUAR, 800_000L, listOf("sepatu")),
        Kasus(30, "F", "dikasih uang sama ayah 1 juta buat bayar kuliah", MASUK, 1_000_000L, null)
    )

    @Test
    fun jalankan30Kalimat() = runBlocking {
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
            println("UJI30|$baris")
        }

        val ringkas = "RINGKASAN dicatat=$dicatat dari ${kasus.size} kasus | " +
            "jenis=$jenisBenar nominal=$nominalBenar item=$itemBenar kategori=$kategoriWajar " +
            "(dari $dicatat yang dicatat) | bertanya benar=$tanyaBenar/$tanyaDiminta | " +
            "menolak benar=$tolakBenar/$tolakDiminta | gagal=$gagal"
        Log.i(TAG, ringkas)
        println("UJI30|$ringkas")

        // Jaminan keras: mesin lokal menjawab semua kalimat tanpa error, dan
        // mayoritas kalimat transaksi benar-benar tercatat.
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
