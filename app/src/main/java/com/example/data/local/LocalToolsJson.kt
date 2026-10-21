package com.example.data.local

/**
 * Bentuk "daftar alat" yang dikirim ke otak lokal.
 *
 * Otak v5 (r1..r8) dilatih dengan daftar kategori DI DALAM `enum` skema tool,
 * `system` cuma baris tanggal, dan HANYA dua alat (catat_pengeluaran,
 * catat_masuk). Aplikasi sebelumnya mengirim bentuk era lama: tiga alat (ada
 * `tanya`), `category` tanpa `enum`, `required` ["amount","category"]. Bentuk
 * itu tidak pernah dilihat model v5, dan hasil di aplikasi anjlok: kategori
 * benar cuma 0,7-4% (lihat vm/CATATAN.md, mode eval `app`).
 *
 * Jadi daftar kategori user WAJIB ikut dikirim, dan bentuknya harus sama
 * persis dengan yang dilihat model saat latihan:
 *
 *  - hanya 2 alat, urutan: catat_pengeluaran lalu catat_masuk
 *  - urutan kunci: name, parameters{type, properties{amount, category, item, date}, required}
 *  - pemisah TANPA spasi (mesin merender `json.dumps(..., separators=(",",":"))`)
 *  - `required` = ["category"]
 *
 * Daftar kategori kosong -> `enum` tidak dikirim, sehingga prefix-nya jatuh ke
 * bentuk tanpa daftar (seperti model v3). Model seperti itu tetap bisa jalan;
 * yang penting prefix tak pernah seen as training.
 */
internal object LocalToolsJson {

    /**
     * @param incomeCategories kategori pemasukan milik user (untuk `catat_masuk`)
     * @param expenseCategories kategori pengeluaran milik user (untuk `catat_pengeluaran`)
     */
    fun build(incomeCategories: List<String>, expenseCategories: List<String>): String {
        val masuk = incomeCategories.cleaned()
        val keluar = expenseCategories.cleaned()
        return "[" +
            tool("catat_pengeluaran", keluar) + "," +
            tool("catat_masuk", masuk) +
            "]"
    }

    /**
     * Buang duplikat/kosong lalu urutkan. Urutan sorted supaya prefix-nya
     * stabil: kalau daftar berubah urutan tiap permintaan, model akan
     * di-inisialisasi ulang terus-menerus. Duplikat dibuang mengabaikan huruf
     * besar-kecil (yang pertama menang) supaya daftar "Gaji" dan "gaji" tidak
     * jadi dua pilihan berbeda.
     *
     * Ejaan user dipertahankan. Data latih memang huruf kecil, tetapi percobaan
     * di HP dengan daftar huruf kecil justru menurunkan kategori bawaan
     * (34 kasus: 19 -> 16; 30 kasus: 16 -> 13), sedangkan keuntungan kategori
     * kustom datang dari pencocokan nama kategori di parser, bukan dari huruf.
     * ponytail: satu variabel, diukur di HP; kalau nanti ada eval 599 mode app
     * untuk kedua ejaan, ganti keputusan ini dengan angka itu.
     */
    private fun List<String>.cleaned(): List<String> {
        val seen = LinkedHashMap<String, String>()
        for (raw in this) {
            val value = raw.trim()
            if (value.isEmpty()) continue
            seen.putIfAbsent(value.lowercase(), value)
        }
        return seen.values.sortedBy { it.lowercase() }
    }

    private fun tool(name: String, categories: List<String>): String {
        val category = if (categories.isEmpty()) {
            """{"type":"string"}"""
        } else {
            """{"type":"string","enum":[${categories.joinToString(",") { quote(it) }}]}"""
        }
        return """{"name":"$name","parameters":{"type":"object","properties":{""" +
            """"amount":{"type":"integer"},"category":$category,""" +
            """"item":{"type":"string"},"date":{"type":"string"}},"required":["category"]}}"""
    }

    /** Escape JSON minimal: cukup untuk nama kategori bebas. */
    private fun quote(value: String): String {
        val sb = StringBuilder(value.length + 2)
        sb.append('"')
        for (ch in value) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (ch < ' ') sb.append("\\u%04x".format(ch.code)) else sb.append(ch)
            }
        }
        sb.append('"')
        return sb.toString()
    }
}
