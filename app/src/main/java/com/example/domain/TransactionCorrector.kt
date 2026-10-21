package com.example.domain

/**
 * Memperbaiki jenis transaksi ("income"/"expense") dari jawaban AI.
 *
 * Model sering menandai uang masuk sebagai pengeluaran ("terima gaji 5 juta"),
 * karena contoh pengeluaran di data latih jauh lebih banyak. Prompt saja tidak
 * menutup celah ini, jadi jawabannya diperiksa terhadap dua hal yang tidak bisa
 * dikarang model: daftar kategori milik user, dan kalimat asli yang diketik user.
 *
 * Aturan bukti, dari yang paling kuat:
 * 1. Kalimat user memakai kata kerja yang jelas satu arah dan tidak menyebut nama
 *    kategori arah lawan -> jenisnya itu. Kalimat user lebih kuat daripada
 *    kategori model: "sewa kos" tetap pengeluaran walau model memilih kategori
 *    pemasukan.
 * 2. Kategori yang dipilih AI sendiri hanya ada di satu daftar -> jenisnya itu.
 * 3. Kalau kategori tidak menentukan (mis. "Lainnya" ada di dua daftar), kalimat
 *    user dipakai, tapi butuh DUA bukti searah sekaligus: kata kerja yang searah
 *    DAN nama kategori user yang disebut, tanpa kata kerja arah sebaliknya.
 *
 * Kata kerja yang arahnya memang dua arah ("transfer", "kirim") tidak pernah
 * dipakai sebagai bukti: "transfer 500 ribu" bisa masuk bisa keluar.
 *
 * Ragunya sengaja dimenangkan oleh jawaban AI: salah menebak di sini berarti
 * memindahkan pemasukan jadi pengeluaran di data keuangan user.
 */
object TransactionCorrector {

    const val TYPE_INCOME = "income"
    const val TYPE_EXPENSE = "expense"

    /** Kata kerja yang menandakan uang masuk ke user. */
    private val INCOME_VERBS = listOf(
        "terima", "menerima", "diterima", "dapat", "dapet", "dapetin", "gajian",
        "dibayar", "dikasih", "diberi", "menang", "cair", "jual", "jualan",
        "refund", "cashback", "warisan"
    )

    /** Kata kerja yang menandakan uang keluar dari user. */
    private val EXPENSE_VERBS = listOf(
        "bayar", "bayarin", "beli", "beliin", "belanja", "sewa", "cicil",
        "angsuran", "transfer", "kirim", "topup", "isi", "ongkos", "parkir", "tarif",
        "jajan", "servis", "cuci", "traktir", "nraktir",
        "nyumbang", "sumbang", "sedekah", "infak"
    )

    /**
     * Kata kerja yang arahnya tidak bisa dipastikan dari kata itu saja.
     *
     * Tetap dihitung sebagai "ini kalimat transaksi", tapi tidak dipakai untuk
     * memutuskan masuk/keluar.
     */
    private val AMBIGUOUS_VERBS = listOf("transfer", "kirim")

    /**
     * Frasa uang masuk yang tidak tertangkap satu kata.
     *
     * "uang kiriman ibu" adalah uang MASUK, sedangkan "kirim uang ke ibu" adalah
     * uang keluar; kata "kirim" saja tidak cukup.
     */
    private val INCOME_PHRASES = listOf(
        "uang kiriman", "kiriman dari", "dikirim",
        "bunga tabungan", "bunga deposito"
    )

    /**
     * Frasa uang masuk yang biasanya membuka kalimat.
     *
     * Dipakai untuk kalimat bertujuan: "dikasih uang sama ayah 1 juta buat bayar
     * kuliah". Di situ "bayar" hanya menyebut TUJUAN uangnya, bukan arah
     * transaksinya, jadi frasa uang masuk yang muncul lebih dulu yang menang.
     */
    private val EARLY_INCOME_PHRASES = listOf(
        "dikasih uang", "dikasih duit", "diberi uang", "diberi duit",
        "dapat uang", "dapet uang", "dapat duit", "dapet duit", "uang dari"
    )

    /**
     * Jenis akhir untuk satu jawaban AI.
     *
     * [aiType] boleh berisi "PEMASUKAN"/"PENGELUARAN"/"income"/"expense". Nilai
     * yang tidak dikenal dianggap pengeluaran, sama seperti perilaku lama, supaya
     * jawaban aneh tidak tiba-tiba tercatat sebagai pemasukan.
     */
    fun correctType(
        userText: String?,
        aiType: String?,
        aiCategory: String?,
        incomeCategories: List<String>,
        expenseCategories: List<String>
    ): String {
        val jenisAi = normalizeType(aiType)

        val adaVerbIncome = hasIncomeSignal(userText)
        val adaVerbExpense = hasExpenseSignal(userText)
        val adaKatIncome = CategoryResolver.mentionsCategory(userText, incomeCategories)
        val adaKatExpense = CategoryResolver.mentionsCategory(userText, expenseCategories)

        // 1. Kalimat user sendiri yang menentukan, selama tidak dibantah nama
        //    kategori arah lawan ("dapat tagihan listrik" memakai "dapat" padahal
        //    pengeluaran). Kata kerja dua arah sudah disaring di helper.
        if (adaVerbExpense && !adaVerbIncome && !adaKatIncome) return TYPE_EXPENSE
        if (adaVerbIncome && !adaVerbExpense && !adaKatExpense) return TYPE_INCOME

        // 1b. Dua kata kerja bercampur: kalau frasa uang masuk muncul lebih dulu,
        //     kata kerja pengeluaran sesudahnya hanya menyebut tujuan uang itu
        //     ("dikasih uang sama ayah 1 juta buat bayar kuliah" tetap pemasukan).
        if (adaVerbIncome && adaVerbExpense && !adaKatExpense) {
            val posisiMasuk = firstPhraseIndex(userText, EARLY_INCOME_PHRASES)
            val posisiKeluar = firstWordIndex(userText, EXPENSE_VERBS)
            if (posisiMasuk >= 0 && (posisiKeluar < 0 || posisiMasuk < posisiKeluar)) {
                return TYPE_INCOME
            }
        }

        // 2. Kategori pilihan AI sendiri. Kalau kategorinya cuma ada di daftar
        //    pemasukan, jenisnya pemasukan -- model sudah "tahu" isinya, yang
        //    salah hanya label jenisnya.
        val cocokIncome =
            CategoryResolver.resolve(aiCategory, incomeCategories) is CategoryResolution.Matched
        val cocokExpense =
            CategoryResolver.resolve(aiCategory, expenseCategories) is CategoryResolution.Matched
        if (cocokIncome && !cocokExpense) return TYPE_INCOME
        if (cocokExpense && !cocokIncome) return TYPE_EXPENSE

        // 3. Kategori belum menentukan. Perlu DUA bukti searah sekaligus: kata
        //    kerja yang searah DAN nama kategori user yang disebut. Satu bukti
        //    saja terlalu gampang salah -- "dapat tagihan listrik" memakai kata
        //    "dapat" padahal pengeluaran.
        if (jenisAi != TYPE_INCOME && adaVerbIncome && adaKatIncome && !adaVerbExpense) {
            return TYPE_INCOME
        }
        if (jenisAi != TYPE_EXPENSE && adaVerbExpense && adaKatExpense && !adaVerbIncome) {
            return TYPE_EXPENSE
        }

        // 4. Tidak ada bukti yang searah: jawaban AI dibiarkan.
        return jenisAi
    }

    /** Ada kata kerja/frasa pemasukan di [text], dan tidak ada kata kerja dua arah. */
    private fun hasIncomeSignal(text: String?): Boolean {
        if (CategoryResolver.containsAnyWord(text, AMBIGUOUS_VERBS)) return false
        return CategoryResolver.containsAnyWord(text, INCOME_VERBS) ||
            containsAnyPhrase(text, INCOME_PHRASES)
    }

    /** Ada kata kerja pengeluaran di [text], dan tidak ada kata kerja dua arah. */
    private fun hasExpenseSignal(text: String?): Boolean {
        if (CategoryResolver.containsAnyWord(text, AMBIGUOUS_VERBS)) return false
        return CategoryResolver.containsAnyWord(text, EXPENSE_VERBS)
    }

    /** Posisi awal frasa pertama dari [phrases] di teks ternormalisasi, atau -1. */
    private fun firstPhraseIndex(text: String?, phrases: List<String>): Int {
        val haystack = CategoryResolver.normalize(text)
        if (haystack.isEmpty()) return -1
        return phrases.map { haystack.indexOf(it) }
            .filter { it >= 0 }
            .minOrNull() ?: -1
    }

    /** Posisi awal kata pertama dari [words] di teks ternormalisasi, atau -1. */
    private fun firstWordIndex(text: String?, words: List<String>): Int {
        val haystack = CategoryResolver.normalize(text)
        if (haystack.isEmpty()) return -1
        val padded = " $haystack "
        return words.map { padded.indexOf(" $it ") }
            .filter { it >= 0 }
            .minOrNull() ?: -1
    }

    /** [text] memuat salah satu [phrases] sebagai frasa utuh. */    private fun containsAnyPhrase(text: String?, phrases: List<String>): Boolean {
        val haystack = CategoryResolver.normalize(text)
        if (haystack.isEmpty()) return false
        val padded = " ${haystack} "
        return phrases.any { padded.contains(" $it ") }
    }

    /**
     * Benar kalau [text] mengandung kata kerja transaksi yang jelas.
     *
     * Dipakai saat model tidak memanggil alat sama sekali: "isi bensin" bukan
     * alasan untuk membalas "bukan transaksi" -- nominalnya yang belum ada, jadi
     * aplikasi harus bertanya nominal. Kalimat tanpa kata kerja ("5jt", "2500000")
     * tidak ikut terhitung di sini; jalur nominal yang menanganinya.
     */
    fun looksLikeTransaction(text: String?): Boolean =
        hasIncomeSignal(text) || hasExpenseSignal(text) ||
            CategoryResolver.containsAnyWord(text, AMBIGUOUS_VERBS)

    /**
     * Arah uang dari kata kerja di [text], atau null kalau buktinya bercampur/tidak ada.
     *
     * Hanya kata kerja yang dipakai: ini untuk menjawab pertanyaan aplikasi sendiri
     * ("Itu uang masuk atau uang keluar?") ketika user cuma mengetik nominalnya.
     * Kalau kedua arah muncul ("terima lalu bayar") atau kata kerjanya dua arah
     * ("transfer"), hasilnya null supaya aplikasi tetap bertanya, bukan menebak.
     */
    fun directionFromVerbs(text: String?): String? {
        val masuk = hasIncomeSignal(text)
        val keluar = hasExpenseSignal(text)
        return when {
            masuk && !keluar -> TYPE_INCOME
            keluar && !masuk -> TYPE_EXPENSE
            else -> null
        }
    }

    /**
     * Jenis dari jawaban AI, dinormalkan ke "income"/"expense".
     *
     * Dipakai jalur simpan dan kartu konfirmasi sekaligus, supaya kartu tidak pernah
     * menampilkan jenis yang berbeda dari yang akhirnya disimpan. Nilai yang tidak
     * dikenal dianggap pengeluaran, sama seperti perilaku lama, supaya jawaban aneh
     * tidak tiba-tiba tercatat sebagai pemasukan.
     */
    fun normalizeType(raw: String?): String {
        val teks = raw?.trim()?.lowercase()
        return if (teks == TYPE_INCOME || teks == "pemasukan") TYPE_INCOME else TYPE_EXPENSE
    }
}
