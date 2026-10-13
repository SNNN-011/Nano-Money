package com.example.data

import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Satu-satunya sumber daftar kategori.
 *
 * Kategori user punya ID stabil: rename hanya mengubah [Category.name] dan mengangkat
 * updatedAt, sehingga budget serta transaksi yang menunjuk ID itu tetap nyambung.
 */
class CategoryRepository(
    private val dao: CategoryDao,
    private val prefs: SharedPreferences
) {

    fun observeAll(): Flow<List<Category>> = dao.observeAll()

    fun observeByType(type: String): Flow<List<Category>> = dao.observeByType(type)

    suspend fun getByType(type: String): List<Category> = dao.getByType(type)

    suspend fun findById(id: String): Category? = dao.findById(id)

    suspend fun countActive(): Int = dao.countActive()

    /** Mencari kategori berdasarkan nama (case-insensitive), untuk resolving id saat tulis. */
    suspend fun findByName(type: String, name: String): Category? =
        dao.getByType(type).firstOrNull { it.name.trim().equals(name.trim(), ignoreCase = true) }

    /** Peta id -> nama terbaru; dipakai tampilan agar rename langsung tercermin. */
    val nameById: Flow<Map<String, String>> = dao.observeAll().map { list ->
        list.associate { it.id to it.name }
    }

    /**
     * Menambahkan kategori baru. ID-nya UUID, bukan legacy deterministik —
     * yang deterministik hanya untuk hasil migrasi prefs.
     */
    suspend fun add(type: String, name: String): Category? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        if (findByName(type, trimmed) != null) return null

        val id = SyncKeys.newCategoryKey()
        val category = Category(
            id = id,
            name = trimmed,
            type = type,
            isDeleted = false,
            updatedAt = System.currentTimeMillis(),
            syncKey = id,
            syncStatus = SyncStatus.PENDING
        )
        dao.upsertAll(listOf(category))
        return category
    }

    /** Rename: ID tetap, hanya nama + updatedAt yang berubah. */
    suspend fun rename(categoryId: String, newName: String): Boolean {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return false
        val existing = dao.findById(categoryId) ?: return false
        if (existing.name == trimmed) return true
        if (findByName(existing.type, trimmed) != null) return false

        dao.upsertAll(
            listOf(
                existing.copy(
                    name = trimmed,
                    updatedAt = System.currentTimeMillis(),
                    syncStatus = SyncStatus.PENDING
                )
            )
        )
        return true
    }

    /** Soft delete; ditolak bila menyisakan nol kategori pada tipenya. */
    suspend fun softDelete(categoryId: String): Boolean {
        val existing = dao.findById(categoryId) ?: return false
        if (dao.countActiveByType(existing.type) <= 1) return false

        dao.upsertAll(
            listOf(
                existing.copy(
                    isDeleted = true,
                    updatedAt = System.currentTimeMillis(),
                    syncStatus = SyncStatus.PENDING
                )
            )
        )
        return true
    }

    // ---- Jalur sinkronisasi ----

    suspend fun getPendingSync(): List<Category> = dao.getPendingSync()

    suspend fun markSynced(syncKeys: List<String>) = dao.markSynced(syncKeys)

    suspend fun applyRemote(remote: Category) = dao.upsertRemote(listOf(remote))

    companion object {
        private const val TAG = "CategoryMigration"

        /**
         * Migrasi sekali dari prefs lama ke tabel categories + category_budgets.
         * Idempoten: kalau sudah pernah berjalan (flag di prefs) dilewati, dan kalau
         * dipaksa ulang, ID legacy yang sama dipakai sehingga tidak ada duplikat.
         */
        suspend fun runIfNeeded(
            categoryDao: CategoryDao,
            budgetDao: CategoryBudgetDao,
            prefs: SharedPreferences
        ): Boolean {
            if (prefs.getBoolean(CategoryMigration.PREF_MIGRATED_FLAG, false)) return false

            val now = System.currentTimeMillis()
            val categories = CategoryMigration.buildCategories(
                incomeCsv = prefs.getString(CategoryMigration.PREF_INCOME_LIST, null),
                expenseCsv = prefs.getString(CategoryMigration.PREF_EXPENSE_LIST, null),
                now = now
            )
            categoryDao.upsertAll(categories)

            val (budgets, orphans) = CategoryMigration.buildBudgets(
                budgetByName = CategoryMigration.collectBudgetPrefs(prefs.all),
                categories = categories
            )
            if (budgets.isNotEmpty()) budgetDao.upsertAll(budgets)
            if (orphans.isNotEmpty()) {
                Log.w(TAG, "Budget tanpa kategori, dilewati: ${orphans.joinToString(", ")}")
            }

            prefs.edit().putBoolean(CategoryMigration.PREF_MIGRATED_FLAG, true).apply()
            return true
        }
    }
}