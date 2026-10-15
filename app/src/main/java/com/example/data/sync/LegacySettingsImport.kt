package com.example.data.sync

import com.example.data.Category
import com.example.data.CategoryBudget
import com.example.data.CategoryMigration
import com.example.data.SyncableSettings
import java.util.Locale

/**
 * Impor sekali dari dokumen preferensi versi lama.
 *
 * Sinkron lawas menulis semua preferensi aplikasi ke satu dokumen
 * `settings/financial_tracker_prefs`, termasuk daftar kategori dan anggaran per
 * kategori. Setelah kategori dipindah ke tabel Room, dokumen itu tidak lagi punya
 * updatedAt sehingga tidak akan pernah terbawa query pull, dan jalur bacanya sudah
 * dihapus. Tanpa impor ini, kategori kustom dan anggaran yang hanya pernah tersimpan
 * di cloud akan hilang.
 *
 * Yang dibaca HANYA daftar kategori dan `category_budget_*`. Dokumen lama juga berisi
 * kunci keamanan (PIN, biometrik); field itu sengaja tidak pernah disentuh di sini,
 * jadi mustahil ikut terimpor ke Room atau dikirim ulang ke cloud.
 */
object LegacySettingsImport {

    /** Id dokumen lawas di koleksi `settings`. */
    const val DOC_ID = "financial_tracker_prefs"

    data class Imported(
        val categories: List<Category>,
        val budgets: List<CategoryBudget>,
        /** Id kategori yang tidak ada, sehingga anggarannya tidak bisa dipasang. */
        val orphanBudgets: List<String>,
        /** M5: anggaran bulanan dari dokumen lawas, atau null bila tidak ada. */
        val monthlyBudgetLimit: Long? = null
    )

    /**
     * Mengubah isi dokumen lawas menjadi baris Room.
     *
     * Kategori/budget yang id-nya sudah ada di [existingCategoryIds] tidak ikut,
     * supaya perubahan lokal (mis. kategori yang sudah di-rename) tidak ditimpa
     * nilai lama dari cloud.
     */
    fun parse(
        docData: Map<String, Any?>,
        existingCategoryIds: Set<String>,
        now: Long
    ): Imported {
        val incomeCsv = docData[CategoryMigration.PREF_INCOME_LIST] as? String
        val expenseCsv = docData[CategoryMigration.PREF_EXPENSE_LIST] as? String

        // Hanya daftar yang benar-benar ada di dokumen yang diproses. Kalau salah
        // satu tidak ada, tidak boleh diisi dengan daftar default: dari cloud kita
        // tidak boleh mengarang kategori yang mungkin memang sudah dihapus user.
        // seedDefaults = false: daftar kosong di cloud berarti user memang menghapus
        // kategori itu, bukan belum ada data. Kalau diisi DEFAULT di sini, semua
        // kategori bawaan yang sudah dihapus akan dibangkitkan ulang.
        val built = buildList {
            if (!incomeCsv.isNullOrBlank()) {
                addAll(
                    CategoryMigration
                        .buildCategories(incomeCsv, null, now, seedDefaults = false)
                        .filter { it.type == CategoryMigration.TYPE_INCOME }
                )
            }
            if (!expenseCsv.isNullOrBlank()) {
                addAll(
                    CategoryMigration
                        .buildCategories(null, expenseCsv, now, seedDefaults = false)
                        .filter { it.type == CategoryMigration.TYPE_EXPENSE }
                )
            }
        }

        val budgetsToConsider = CategoryMigration.collectBudgetPrefs(docData)
        val (budgets, orphans) = CategoryMigration.buildBudgets(budgetsToConsider, built)

        // Anggaran boleh dipasang ke kategori yang sudah ada di perangkat ini juga,
        // bukan cuma yang ikut terimpor.
        val knownIds = existingCategoryIds + built.map { it.id }

        // M5: dokumen lawas juga memuat anggaran bulanan. Tanpa ikut diimpor, nilainya
        // hilang saat dokumen itu dihapus setelah migrasi.
        val monthlyBudget = (docData[SyncableSettings.MONTHLY_BUDGET_LIMIT] as? Number)?.toLong()

        return Imported(
            categories = built.filter { it.id !in existingCategoryIds },
            budgets = budgets.filter { it.categoryId in knownIds },
            orphanBudgets = orphans,
            monthlyBudgetLimit = monthlyBudget
        )
    }

    /** Nama field yang boleh dibaca dari dokumen lawas. */
    fun isAllowedField(key: String): Boolean =
        key.lowercase(Locale.ROOT) == CategoryMigration.PREF_INCOME_LIST ||
            key.lowercase(Locale.ROOT) == CategoryMigration.PREF_EXPENSE_LIST ||
            key.lowercase(Locale.ROOT) == SyncableSettings.MONTHLY_BUDGET_LIMIT ||
            key.lowercase(Locale.ROOT).startsWith(CategoryMigration.PREF_BUDGET_PREFIX)

    /** Hanya baris field yang diizinkan, sebagai pengaman tambahan. */
    fun onlyAllowedFields(docData: Map<String, Any?>): Map<String, Any?> =
        docData.filterKeys { isAllowedField(it) }
}
