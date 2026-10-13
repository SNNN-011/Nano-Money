package com.example.data

/**
 * Efek samping kategori terhadap data transaksi.
 *
 * Rename kategori ikut menulis nama baru ke transaksi (categoryId tidak berubah),
 * dan kategori yang dihapus memindahkan transaksinya ke kategori pengganti.
 * Keduanya ditandai PENDING supaya perubahannya ikut tersinkron ke perangkat lain.
 */
class CategoryCascadeService(private val recordDao: FinancialRecordDao) {

    /** Mengembalikan jumlah transaksi yang namanya ikut diperbarui. */
    suspend fun applyRename(categoryId: String, newName: String): Int =
        recordDao.renameCategoryOnRecords(
            categoryId = categoryId,
            newName = newName,
            now = System.currentTimeMillis()
        )

    /** Memindahkan seluruh transaksi dari kategori dihapus ke [target]. */
    suspend fun applyMove(oldCategoryId: String, target: Category): Int =
        recordDao.moveRecordsToCategory(
            oldCategoryId = oldCategoryId,
            newCategoryId = target.id,
            newName = target.name,
            now = System.currentTimeMillis()
        )
}