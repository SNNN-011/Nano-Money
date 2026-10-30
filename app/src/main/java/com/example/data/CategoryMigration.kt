package com.example.data

import java.util.Locale

/**
 * Migrasi sekali dari SharedPreferences lama ke tabel Room.
 *
 * Data lama sumbernya cuma string: `income_categories_list`, `expense_categories_list`,
 * dan `category_budget_<nama>`. Fungsi di sini murni (tanpa Android) supaya bisa diuji,
 * dan sengaja idempoten: dijalankan dua kali menghasilkan himpunan baris yang sama.
 */
object CategoryMigration {

    const val PREF_INCOME_LIST = "income_categories_list"
    const val PREF_EXPENSE_LIST = "expense_categories_list"
    const val PREF_BUDGET_PREFIX = "category_budget_"
    const val PREF_MIGRATED_FLAG = "categories_migrated_v1"

    const val TYPE_INCOME = "income"
    const val TYPE_EXPENSE = "expense"

    val DEFAULT_INCOME = listOf(
        "Gaji", "Uang Saku", "Freelance", "Bisnis",
        "Investasi", "Bonus", "Hadiah", "Lainnya"
    )
    val DEFAULT_EXPENSE = listOf(
        "Makanan & Minuman", "Transportasi", "Belanja", "Kebutuhan Rumah",
        "Tagihan & Utilitas", "Pendidikan", "Kesehatan", "Hiburan",
        "Langganan", "Internet & Pulsa", "Perawatan Diri", "Donasi", "Lainnya"
    )

    /**
     * Nama bawaan lama -> nama bawaan baru.
     *
     * Hanya dipakai untuk baris yang masih persis hasil seeding: id legacy
     * deterministik dan namanya belum pernah diubah user. Rename mempertahankan
     * id, jadi transaksi dan budget yang menunjuk id itu tetap nyambung.
     */
    private val RENAMED_DEFAULTS = mapOf(
        "Makanan" to "Makanan & Minuman",
        "Tagihan" to "Tagihan & Utilitas"
    )

    /** Rencana upgrade daftar bawaan: rename baris lama + tambah baris yang belum ada. */
    data class DefaultUpgradePlan(
        val renames: List<Category>,
        val inserts: List<Category>
    )

    /**
     * Menyusun perubahan daftar bawaan untuk database yang sudah berisi kategori.
     *
     * Aturannya sengaja konservatif:
     * - rename hanya untuk baris hasil seeding (id legacy) yang namanya masih lama;
     *   kategori buatan user atau yang sudah diganti namanya tidak disentuh;
     * - kategori yang sudah dihapus user tidak dihidupkan lagi, termasuk penerusnya;
     * - nama yang sudah ada (walau sudah dihapus) tidak diduplikasi.
     *
     * Murni dan idempoten: menjalankan rencana ini dua kali tidak menghasilkan
     * perubahan kedua.
     */
    fun planDefaultUpgrade(existing: List<Category>, now: Long): DefaultUpgradePlan {
        val names = existing.map { it.type to normalize(it.name) }.toMutableSet()
        val ids = existing.map { it.id }.toMutableSet()
        val renames = mutableListOf<Category>()
        val blocked = mutableSetOf<Pair<String, String>>()

        existing.forEach { row ->
            if (row.type != TYPE_EXPENSE) return@forEach
            val baru = RENAMED_DEFAULTS.entries
                .firstOrNull { it.key.equals(row.name.trim(), ignoreCase = true) }
                ?.value ?: return@forEach
            if (row.id != legacyId(row.type, row.name)) return@forEach

            val kunciBaru = row.type to normalize(baru)
            if (row.isDeleted) {
                blocked += kunciBaru
                return@forEach
            }
            if (kunciBaru in names) return@forEach

            renames += row.copy(name = baru, updatedAt = now, syncStatus = SyncStatus.PENDING)
            names += kunciBaru
        }

        val inserts = mutableListOf<Category>()
        listOf(TYPE_INCOME to DEFAULT_INCOME, TYPE_EXPENSE to DEFAULT_EXPENSE).forEach { (type, daftar) ->
            daftar.forEach { name ->
                val kunci = type to normalize(name)
                if (kunci in blocked || kunci in names) return@forEach
                val id = legacyId(type, name)
                if (id in ids) return@forEach

                inserts += Category(
                    id = id,
                    name = name,
                    type = type,
                    isDeleted = false,
                    updatedAt = now,
                    syncKey = id,
                    syncStatus = SyncStatus.PENDING
                )
                names += kunci
                ids += id
            }
        }
        return DefaultUpgradePlan(renames, inserts)
    }

    /** Memecah daftar kategori yang tersimpan sebagai CSV. */
    fun parseCategoryList(csv: String?): List<String> =
        csv.orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    /**
     * ID legacy yang deterministik, hanya untuk hasil migrasi prefs.
     * Dua perangkat yang bermigrasi dari prefs sama menghasilkan ID sama, sehingga
     * tidak ada kategori ganda setelah keduanya sync.
     */
    fun legacyId(type: String, name: String): String = "cat:$type:${normalize(name)}"

    /** Normalisasi nama: huruf kecil, spasi/pungliat jadi tanda hubung. */
    fun normalize(name: String): String =
        name.trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')

    /**
     * Membangun baris `categories` dari kedua daftar prefs.
     * ID memakai [legacyId] supaya idempoten antar perangkat.
     *
     * [seedDefaults] harus false kalau sumbernya adalah cloud. Di sana daftar
     * kategori kosong berarti "user memang menghapus semuanya", bukan "belum ada
     * data". Mengisi dengan [DEFAULT_EXPENSE] di jalur itu akan membangkitkan
     * kategori yang sudah dihapus, persis seperti yang harus dicegah.
     */
    fun buildCategories(
        incomeCsv: String?,
        expenseCsv: String?,
        now: Long,
        seedDefaults: Boolean = true
    ): List<Category> {
        val result = mutableListOf<Category>()

        fun addAll(type: String, names: List<String>) {
            val seen = mutableSetOf<String>()
            names.forEach { raw ->
                val name = raw.trim()
                if (name.isEmpty()) return@forEach
                val id = legacyId(type, name)
                if (!seen.add(id)) return@forEach
                result += Category(
                    id = id,
                    name = name,
                    type = type,
                    isDeleted = false,
                    updatedAt = now,
                    syncKey = id,
                    syncStatus = SyncStatus.PENDING
                )
            }
        }

        addAll(TYPE_INCOME, parseCategoryList(incomeCsv).let { if (seedDefaults && it.isEmpty()) DEFAULT_INCOME else it })
        addAll(TYPE_EXPENSE, parseCategoryList(expenseCsv).let { if (seedDefaults && it.isEmpty()) DEFAULT_EXPENSE else it })
        return result
    }

    /**
     * Memetakan prefs `category_budget_<nama>` ke baris `category_budgets` berdasarkan
     * categoryId. Budget yang namanya tak ada di daftar kategori dibuang (di-log caller),
     * supaya tidak tercipta budget yatim.
     */
    fun buildBudgets(
        budgetByName: Map<String, Long>,
        categories: List<Category>
    ): Pair<List<CategoryBudget>, List<String>> {
        val budgets = mutableListOf<CategoryBudget>()
        val orphans = mutableListOf<String>()
        val byName = categories.associateBy { it.name.trim().lowercase(Locale.ROOT) }

        budgetByName.forEach { (rawName, rawLimit) ->
            val name = rawName.trim()
            if (name.isEmpty() || rawLimit <= 0L) return@forEach
            // Prefs lama masih memakai nama bawaan lama ("Makanan", "Tagihan").
            // Setelah daftar bawaan berganti nama, budget itu harus tetap
            // menemukan kategorinya, bukan dibuang sebagai yatim.
            val category = byName[name.lowercase(Locale.ROOT)]
                ?: RENAMED_DEFAULTS.entries
                    .firstOrNull { it.key.equals(name, ignoreCase = true) }
                    ?.let { byName[it.value.trim().lowercase(Locale.ROOT)] }
            if (category == null) {
                orphans += name
                return@forEach
            }
            budgets += CategoryBudget(
                categoryId = category.id,
                budgetLimit = rawLimit,
                isDeleted = false,
                updatedAt = System.currentTimeMillis(),
                syncKey = category.id,
                syncStatus = SyncStatus.PENDING
            )
        }
        return budgets to orphans
    }

    /** Mengambil semua prefs `category_budget_<nama>` jadi peta nama -> limit. */
    fun collectBudgetPrefs(allPrefs: Map<String, *>): Map<String, Long> =
        allPrefs.entries
            .filter { it.key.startsWith(PREF_BUDGET_PREFIX) }
            .mapNotNull { entry ->
                val value = when (val v = entry.value) {
                    is Long -> v
                    is Int -> v.toLong()
                    is Float -> v.toLong()
                    is Double -> v.toLong()
                    is Short -> v.toLong()
                    else -> null
                }
                if (value == null) null else entry.key.removePrefix(PREF_BUDGET_PREFIX) to value
            }
            .toMap()
}