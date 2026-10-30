package com.example.domain

/**
 * Penunjuk kategori dari kata kunci yang diambil dari **data latih**, bukan dikarang.
 *
 * Model lokal sering memilih kategori pertama yang familiar ("Makanan") untuk item
 * yang jelas bukan makanan, mis. "beli bensin". Diuji pada 200 baris simulasi
 * aplikasi (`vm/measure_hint.py`): kategori benar naik 29,5% -> 37,8%.
 *
 * Kata yang ditambahkan sesudah audit 189 salah kategori (`draf_kontras_v1/AUDIT_189.md`)
 * diberi tanda di daftarnya. Diukur pada 554 baris beroutput `gold_v6/eval_599.jsonl`
 * lewat `vm/measure_app_layer.py`: kategori 67,9% -> 90,4%, jenis 97,7% -> 99,6%.
 * Kustom 78,6% -> 98,8%, bawaan 66,0% -> 88,9%; 3 baris yang berubah salah semuanya
 * gold `lainnya` untuk kalimat yang jelas kesehatan ("bayar Tes Covid", "tes lab").
 * "topup" dibuang dari Hiburan karena "topup pulsa" itu Tagihan, sedangkan
 * "topup game" sudah tertangkap kata "game".
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
        "martabak", "taichan", "tahu", "mcd", "warung", "nraktir", "traktir",
        "sarapan", "makan",
        "makanan", "minuman", "minum", "camilan", "bubur", "soto", "bakwan",
        "ikan", "sayur", "buah", "kantin", "kafe", "cafe", "resto", "restoran",
        "gofood", "grabfood", "shopeefood", "es", "aqua", "jus", "susu", "kue",
        "donat", "pizza", "burger", "sushi", "ramen", "bihun", "nugget", "sosis",
        "kedai", "food court"
    )

    private val TRANSPORTASI = listOf(
        "spbu", "bus", "tol", "parkir", "grab", "pertamina", "bensin", "maxim",
        "paxel", "pertamini", "jne", "indriver", "anterin", "bengkel", "gojek",
        "shell", "tarif", "ojek", "sicepat", "angkot", "kereta", "tambal", "ban",
        "servis motor", "servis mobil", "cuci motor", "cuci mobil",
        "ongkos", "ongkir", "kurir", "jnt", "ekspedisi", "bbm", "pertalite",
        "pertamax", "solar", "dexlite", "ojol", "krl", "mrt", "lrt",
        "transjakarta", "busway", "damri", "kapal", "pesawat", "bandara",
        "terminal", "etoll", "taksi", "gocar", "grabcar", "j&t", "e-toll"
    )

    private val TAGIHAN = listOf(
        "iuran", "listrik", "kos", "gas", "wifi", "kuota", "galon", "indihome",
        "pln", "telkomsel", "lpg", "bpjs", "pulsa", "internet", "smartfren",
        "sampah", "keamanan", "pdam", "token", "denda", "kontrakan", "asuransi",
        "cicilan", "angsuran", "tagihan",
        "shopeepay", "linkaja", "gopay", "spp", "ukt", "kosan", "elpiji",
        "leasing", "adira", "pajak", "stnk", "pbb", "retribusi",
        "first media", "biznet", "myrepublic", "paket data"
    )

    private val HIBURAN = listOf(
        "bioskop", "loket", "nonton", "viu", "steam", "disney", "game",
        "konser", "genflix", "traveloka", "spotify", "cgv", "xxi", "langganan",
        "film", "nintendo", "eshop", "iflix", "netflix", "youtube", "wetv", "vidio",
        "store",
        "rekreasi", "liburan", "wisata", "tamasya", "karaoke", "biliar",
        "bowling", "dufan", "ancol", "waterpark",
        "jalan-jalan", "jalan jalan", "kebun binatang"
    )

    private val BELANJA = listOf(
        "baju", "tokopedia", "lazada", "hypermart", "giant", "sabun", "handuk",
        "alfamidi", "baterai", "perabot", "shopee", "alfamart", "matahari",
        "bhinneka", "sprei", "erafone", "sampo", "indomaret", "electronic",
        "transmart", "lotte", "bukalapak", "tas", "sepatu", "superindo", "deterjen",
        "ramayana", "blibli", "celana", "ibox", "minyak", "kado",
        "kaos", "kemeja", "jaket", "hoodie", "sandal", "sendal", "jilbab",
        "hijab", "mukena", "skincare", "odol", "pewangi", "beras", "gula",
        "garam", "supermarket", "minimarket", "pasar", "peralatan", "popok",
        "pampers", "mainan", "buku", "pulpen",
        "pasta gigi", "sikat gigi", "susu bayi", "alat tulis"
    )

    private val KESEHATAN = listOf(
        "obat", "apotek", "dokter", "klinik", "vaksin", "sakit", "rumah sakit",
        "sanitizer", "vitamin", "plester", "bidan", "gigi", "periksa", "lab",
        "tes", "laboratorium", "mcu", "suplemen", "multivitamin", "perban",
        "betadine", "masker", "puskesmas", "posyandu", "ugd", "igd", "operasi",
        "apotik", "optik", "kacamata", "fisioterapi", "terapi", "imunisasi",
        "cek darah", "medical check up", "rawat inap"
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

    private val JUALAN_ONLINE = listOf("lapak", "penjualan", "marketplace")

    private val GAJI = listOf(
        "gaji", "gajian", "bonus", "tunjangan", "honor", "upah", "lembur",
        "komisi", "thr", "insentif",
        "pesangon", "beasiswa", "pensiun", "payroll", "slip gaji"
    )

    private val INVESTASI = listOf(
        "dividen", "reksadana", "saham", "obligasi", "deposito", "tabungan",
        "emas", "pegadaian", "crypto", "kripto", "bitcoin", "btc", "ethereum",
        "ajaib", "pluang", "profit", "sukuk", "sbn", "logam mulia"
    )

    /**
     * Kategori penampung untuk pemasukan yang tidak punya pemilik jelas.
     *
     * "hadiah undian" bukan Investasi hanya karena model menebak begitu.
     */
    private val LAINNYA = listOf(
        "undian", "kiriman", "cashback",
        "hibah", "kasbon", "warisan", "rezeki", "titipan", "transferan", "hadiah"
    )

    private val KUCING = listOf("whiskas", "pasir kucing", "makanan kucing")

    private val FREELANCE = listOf(
        "freelance", "proyek", "project", "klien", "pelanggan", "omzet",
        "jualan", "orderan", "pesanan", "fee", "desain", "design",
        "uang jasa"
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
        tambah("kucing", KUCING)
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
