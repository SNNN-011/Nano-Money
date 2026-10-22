package com.example.domain

/**
 * Penunjuk kategori dari kata kunci yang diambil dari **data latih**, bukan dikarang.
 *
 * Model lokal sering memilih kategori pertama yang familiar ("Makanan") untuk item
 * yang jelas bukan makanan, mis. "beli bensin". Diuji pada 200 baris simulasi
 * aplikasi (`vm/measure_hint.py`): kategori benar naik 29,5% -> 37,8%.
 *
 * Sebagian besar daftar kata di bawah adalah kata yang paling sering muncul
 * sebagai `item` untuk tiap kategori di `draf_v5/finetune.jsonl`
 * (`vm/build_category_hints.py`), sudah disaring dari kata ambigu. Kata yang
 * muncul di lebih dari satu kategori tidak dipakai.
 *
 * Beberapa kelompok (kesehatan, donasi, servis motor, jualan online) sengaja
 * memakai nama kategori di luar bawaan. Itu tetap aman: [suggest] hanya
 * mengembalikan nama yang benar-benar ada di daftar user, jadi kelompok itu
 * dilewati kalau user tidak punya kategorinya.
 *
 * Penunjuk ini hanya **menyarankan**; yang menentukan tetap kategori milik user.
 * Kalau kategorinya tidak ada di daftar user ([CategoryResolution.Matched] gagal),
 * saran dilewati dan jawaban model dibiarkan -- jangan sampai user yang sudah
 * mengganti nama kategori malah dipaksa ke kategori bawaan.
 */
object CategoryHints {

    /** Nama kategori bawaan aplikasi yang menjadi tujuan tiap kelompok kata. */
    private val MAKANAN = listOf(
        "nasi", "bakso", "kopi", "mie", "ayam", "sate", "teh", "gorengan", "bakmie",
        "padang", "solaria", "dimsum", "hokben", "pecel", "lele", "kopken", "geprek",
        "bensu", "telur", "warteg", "gacoan", "tempe", "seblak", "dunkin", "kenangan",
        "roti", "kerupuk", "yoshinoya", "mineral", "kfc", "jco", "starbucks", "snack",
        "martabak", "taichan", "tahu", "mcd", "warung", "nraktir", "traktir"
    )

    private val TRANSPORTASI = listOf(
        "spbu", "bus", "tol", "parkir", "grab", "pertamina", "bensin", "maxim",
        "paxel", "pertamini", "jne", "indriver", "anterin", "bengkel", "gojek",
        "shell", "tarif", "ojek", "sicepat", "angkot", "kereta", "tambal", "ban",
        "servis motor", "servis mobil", "cuci motor", "cuci mobil"
    )

    private val TAGIHAN = listOf(
        "iuran", "listrik", "kos", "gas", "wifi", "kuota", "galon", "indihome",
        "pln", "telkomsel", "lpg", "bpjs", "pulsa", "internet", "smartfren",
        "sampah", "keamanan", "pdam", "token", "denda", "kontrakan"
    )

    private val HIBURAN = listOf(
        "bioskop", "loket", "nonton", "viu", "steam", "disney", "game", "topup",
        "konser", "genflix", "traveloka", "spotify", "cgv", "xxi", "langganan",
        "film", "nintendo", "eshop", "iflix", "netflix", "youtube", "wetv", "vidio",
        "store"
    )

    private val BELANJA = listOf(
        "baju", "tokopedia", "lazada", "hypermart", "giant", "sabun", "handuk",
        "alfamidi", "baterai", "perabot", "shopee", "alfamart", "matahari",
        "bhinneka", "sprei", "erafone", "sampo", "indomaret", "electronic",
        "transmart", "lotte", "bukalapak", "tas", "sepatu", "superindo", "deterjen",
        "ramayana", "blibli", "celana", "ibox", "minyak", "kado"
    )

    private val KESEHATAN = listOf(
        "obat", "apotek", "dokter", "klinik", "vaksin", "sakit", "rumah sakit"
    )

    /**
     * Kata untuk kategori buatan user yang umum dipakai.
     *
     * Nama tujuan di sini ("donasi", "servis motor") BUKAN kategori bawaan.
     * Kalau user tidak punya kategori dengan nama itu, saran otomatis dilewati
     * oleh [suggest], sama seperti kata kunci lain.
     */
    private val DONASI = listOf(
        "donasi", "sumbang", "nyumbang", "sedekah", "infak", "zakat", "amal",
        "masjid", "panti"
    )

    private val SERVIS_MOTOR = listOf("oli")

    private val JUALAN_ONLINE = listOf("lapak")

    private val GAJI = listOf(
        "gaji", "gajian", "bonus", "tunjangan", "honor", "upah", "lembur",
        "komisi", "thr", "insentif"
    )

    private val INVESTASI = listOf(
        "dividen", "reksadana", "saham", "obligasi", "deposito", "tabungan"
    )

    /**
     * Kategori penampung untuk pemasukan yang tidak punya pemilik jelas.
     *
     * "hadiah undian" bukan Investasi hanya karena model menebak begitu.
     */
    private val LAINNYA = listOf("undian")

    private val FREELANCE = listOf(
        "freelance", "proyek", "project", "klien", "pelanggan", "omzet"
    )

    /**
     * Sasaran cadangan kalau kategori utama tidak dimiliki user.
     *
     * Obat dan apotek tetap belanja kalau user tidak punya kategori Kesehatan;
     * tanpa cadangan ini model bebas memilih kategori yang tidak berhubungan.
     */
    private val FALLBACKS = mapOf("kesehatan" to "belanja")

    /**
     * Pasangan kata -> nama kategori bawaan.
     *
     * Diurutkan dari kata terpanjang supaya "topup pulsa" tidak keburu ditangkap
     * kata yang lebih pendek, dan "alfamart" tidak jatuh ke "mart".
     */
    private val KEYWORDS: List<Pair<String, String>> = buildList {
        fun tambah(kategori: String, kata: List<String>) {
            kata.forEach { add(it to kategori) }
        }
        tambah("belanja", BELANJA)
        tambah("hiburan", HIBURAN)
        tambah("makanan", MAKANAN)
        tambah("tagihan", TAGIHAN)
        tambah("transportasi", TRANSPORTASI)
        tambah("gaji", GAJI)
        tambah("investasi", INVESTASI)
        tambah("freelance", FREELANCE)
        tambah("kesehatan", KESEHATAN)
        tambah("donasi", DONASI)
        tambah("servis motor", SERVIS_MOTOR)
        tambah("jualan online", JUALAN_ONLINE)
        tambah("lainnya", LAINNYA)
    }.sortedByDescending { it.first.length }

    /**
     * Kategori milik user yang paling cocok dengan kata kunci di [text], atau null.
     *
     * [text] boleh kalimat user maupun `item` dari model; `item` lebih diutamakan
     * pemanggil karena lebih pendek dan sudah dibersihkan model.
     */
    fun suggest(text: String?, candidates: List<String>): String? {
        if (text.isNullOrBlank() || candidates.isEmpty()) return null

        val haystack = CategoryResolver.normalize(text)
        if (haystack.isEmpty()) return null

        fun disebut(kata: String): Boolean =
            Regex("(^|[^a-z0-9])${Regex.escape(kata)}([^a-z0-9]|$)").containsMatchIn(haystack)

        // Utama: kata kunci menuju kategorinya sendiri.
        KEYWORDS.forEach { (kata, kategori) ->
            if (!disebut(kata)) return@forEach
            val hasil = CategoryResolver.resolve(kategori, candidates)
            if (hasil is CategoryResolution.Matched) return hasil.category
        }

        // Cadangan: hanya untuk kata kunci yang kategorinya tidak dimiliki user.
        KEYWORDS.forEach { (kata, kategori) ->
            val cadangan = FALLBACKS[kategori] ?: return@forEach
            if (!disebut(kata)) return@forEach
            val hasil = CategoryResolver.resolve(cadangan, candidates)
            if (hasil is CategoryResolution.Matched) return hasil.category
        }
        return null
    }
}
