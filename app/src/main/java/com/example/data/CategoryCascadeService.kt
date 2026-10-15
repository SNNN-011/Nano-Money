package com.example.data

import kotlinx.coroutines.flow.first

/**
 * Efek samping kategori terhadap data transaksi.
 *
 * Rename kategori ikut menulis nama baru ke transaksi (categoryId tidak berubah),
 * dan kategori yang dihapus memindahkan transaksinya ke kategori pengganti.
 * Keduanya ditandai PENDING supaya perubahannya ikut tersinkron ke perangkat lain.
 *
 * Semua method di sini dirancang dipanggil dari dalam satu transaksi Room
 * (`db.withTransaction` di ViewModel) supaya cascade hapus kategori bersifat
 * atomik: gagal di tengah = tidak ada state setengah jalan.
 */
class CategoryCascadeService(private val recordDao: FinancialRecordDao) {

    /** Mengembalikan jumlah transaksi yang namanya ikut diperbarui. */
    suspend fun applyRename(categoryId: String, newName: String): Int =
        recordDao.renameCategoryOnRecords(
            categoryId = categoryId,
            newName = newName,
            now = System.currentTimeMillis()
        )

    /**
     * Memindahkan seluruh transaksi dari kategori yang dihapus ke [target].
     *
     * Query DAO terbatas pada baris aktif (`isDeleted = 0`), jadi baris Sampah
     * dipindah lewat update per baris supaya tidak menunjuk kategori mati saat
     * dipulihkan nanti. Jumlah baris Sampah biasanya kecil.
     */
    suspend fun applyMove(oldCategoryId: String, target: Category): Int {
        val now = System.currentTimeMillis()
        val moved = recordDao.moveRecordsToCategory(
            oldCategoryId = oldCategoryId,
            newCategoryId = target.id,
            newName = target.name,
            now = now
        )

        val trashed = recordDao.getDeletedRecords().first().filter { it.categoryId == oldCategoryId }
        trashed.forEach { row ->
            recordDao.updateRecord(
                row.copy(
                    categoryId = target.id,
                    category = target.name,
                    updatedAt = now,
                    syncStatus = SyncStatus.PENDING
                )
            )
        }
        return moved + trashed.size
    }
}
