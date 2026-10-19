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
 * 1. Kategori yang dipilih AI sendiri hanya ada di satu daftar -> jenisnya itu.
 * 2. Kalau kategori tidak menentukan (mis. "Lainnya" ada di dua daftar), kalimat
 *    user dipakai, tapi butuh DUA bukti searah sekaligus: kata kerja yang searah
 *    DAN nama kategori user yang disebut, tanpa kata kerja arah sebaliknya.
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
        "refund", "cashback"
    )

    /** Kata kerja yang menandakan uang keluar dari user. */
    private val EXPENSE_VERBS = listOf(
        "bayar", "bayarin", "beli", "beliin", "belanja", "sewa", "cicil",
        "angsuran", "transfer", "kirim", "topup", "isi"
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

        // 1. Kategori pilihan AI sendiri. Kalau kategorinya cuma ada di daftar
        //    pemasukan, jenisnya pemasukan -- model sudah "tahu" isinya, yang
        //    salah hanya label jenisnya.
        val cocokIncome =
            CategoryResolver.resolve(aiCategory, incomeCategories) is CategoryResolution.Matched
        val cocokExpense =
            CategoryResolver.resolve(aiCategory, expenseCategories) is CategoryResolution.Matched
        if (cocokIncome && !cocokExpense) return TYPE_INCOME
        if (cocokExpense && !cocokIncome) return TYPE_EXPENSE

        // 2. Kategori belum menentukan. Baca kalimat user: perlu kata kerja searah
        //    DAN nama kategori yang searah. Satu bukti saja terlalu gampang salah
        //    -- "dapat tagihan listrik" memakai kata "dapat" padahal pengeluaran.
        val adaVerbIncome = CategoryResolver.containsAnyWord(userText, INCOME_VERBS)
        val adaVerbExpense = CategoryResolver.containsAnyWord(userText, EXPENSE_VERBS)
        val adaKatIncome = CategoryResolver.mentionsCategory(userText, incomeCategories)
        val adaKatExpense = CategoryResolver.mentionsCategory(userText, expenseCategories)

        if (jenisAi != TYPE_INCOME && adaVerbIncome && adaKatIncome && !adaVerbExpense) {
            return TYPE_INCOME
        }
        if (jenisAi != TYPE_EXPENSE && adaVerbExpense && adaKatExpense && !adaVerbIncome) {
            return TYPE_EXPENSE
        }

        // 3. Tidak ada bukti yang searah: jawaban AI dibiarkan.
        return jenisAi
    }

    private fun normalizeType(raw: String?): String {
        val teks = raw?.trim()?.lowercase()
        return if (teks == TYPE_INCOME || teks == "pemasukan") TYPE_INCOME else TYPE_EXPENSE
    }
}
