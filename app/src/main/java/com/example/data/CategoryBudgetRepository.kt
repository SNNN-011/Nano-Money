package com.example.data

import kotlinx.coroutines.flow.Flow

/**
 * Batas anggaran per kategori. Terikat ke [CategoryBudget.categoryId] (bukan nama),
 * sehingga rename kategori tidak membuat budget yatim.
 */
class CategoryBudgetRepository(private val dao: CategoryBudgetDao) {

    fun observeAll(): Flow<List<CategoryBudget>> = dao.observeAll()

    suspend fun findByCategoryId(categoryId: String): CategoryBudget? =
        dao.findByCategoryId(categoryId)

    /**
     * Menyimpan batas anggaran. Limit <= 0 berarti dicabut: baris ditandai soft delete
     * supaya pencabutan itu ikut tersinkron, bukan sekadar hilang dari daftar.
     */
    suspend fun setLimit(categoryId: String, limit: Long) {
        val existing = dao.findByCategoryId(categoryId)
        val now = System.currentTimeMillis()
        val removed = limit <= 0L

        if (existing == null) {
            if (removed) return
            dao.upsert(
                CategoryBudget(
                    categoryId = categoryId,
                    budgetLimit = limit,
                    isDeleted = false,
                    updatedAt = now,
                    syncKey = categoryId,
                    syncStatus = SyncStatus.PENDING
                )
            )
            return
        }

        dao.upsert(
            existing.copy(
                budgetLimit = if (removed) 0L else limit,
                isDeleted = removed,
                updatedAt = now,
                syncStatus = SyncStatus.PENDING
            )
        )
    }

    // ---- Jalur sinkronisasi ----

    suspend fun getPendingSync(): List<CategoryBudget> = dao.getPendingSync()

    suspend fun markSynced(syncKeys: List<String>) = dao.markSynced(syncKeys)

    suspend fun applyRemote(remote: CategoryBudget) = dao.upsertRemote(listOf(remote))
}