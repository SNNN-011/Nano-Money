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

    val DEFAULT_INCOME = listOf("Gaji", "Investasi", "Freelance", "Lainnya")
    val DEFAULT_EXPENSE = listOf("Makanan", "Transportasi", "Tagihan", "Hiburan", "Belanja", "Lainnya")

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
     */
    fun buildCategories(incomeCsv: String?, expenseCsv: String?, now: Long): List<Category> {
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

        addAll(TYPE_INCOME, parseCategoryList(incomeCsv).ifEmpty { DEFAULT_INCOME })
        addAll(TYPE_EXPENSE, parseCategoryList(expenseCsv).ifEmpty { DEFAULT_EXPENSE })
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
            val category = byName[name.lowercase(Locale.ROOT)]
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