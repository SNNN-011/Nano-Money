package com.example.domain

import com.example.data.FinancialRecord

class CsvImportUseCase {
    // Parses a single CSV line acknowledging quotes and double quotes escape
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = java.lang.StringBuilder()
        var inQuotes = false
        var i = 0
        var sepChar = ','
        
        // Pre-scan line for separator character outside of quotes
        var scanInQuotes = false
        for (char in line) {
            when (char) {
                '"' -> scanInQuotes = !scanInQuotes
                ';' -> {
                    if (!scanInQuotes) {
                        sepChar = ';'
                        break
                    }
                }
            }
        }
        
        while (i < line.length) {
            val c = line[i]
            if (c == '"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    current.append('"')
                    i += 2
                } else {
                    inQuotes = !inQuotes
                    i++
                }
            } else if (c == sepChar && !inQuotes) {
                result.add(current.toString())
                current = java.lang.StringBuilder()
                i++
            } else {
                current.append(c)
                i++
            }
        }
        result.add(current.toString())
        return result
    }

    // Attempt to parse standard date configurations
    private fun tryParseDate(dateStr: String): Long? {
        val formats = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd",
            "dd/MM/yyyy HH:mm:ss",
            "dd/MM/yyyy",
            "dd-MM-yyyy HH:mm:ss",
            "dd-MM-yyyy",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy/MM/dd"
        )
        for (fmt in formats) {
            try {
                val sdf = java.text.SimpleDateFormat(fmt, java.util.Locale.getDefault())
                sdf.isLenient = false
                return sdf.parse(dateStr)?.time
            } catch (e: Exception) {
                // Try next pattern
            }
        }
        return null
    }

    // Import from spreadsheet Excel / CSV
    fun parseCsvString(csvStr: String): List<FinancialRecord> {
        val lines = csvStr.split(Regex("\\r?\\n"))
        if (lines.isEmpty()) throw IllegalArgumentException("File CSV kosong.")
        
        val firstLine = lines.firstOrNull { it.trim().isNotEmpty() } ?: throw IllegalArgumentException("File CSV tidak memiliki data.")
        val headers = parseCsvLine(firstLine).map { it.trim().lowercase() }
        
        val descIndex = headers.indexOfFirst { it.contains("deskripsi") || it.contains("description") }
        val amountIndex = headers.indexOfFirst { it.contains("jumlah") || it.contains("amount") }
        val typeIndex = headers.indexOfFirst { it.contains("tipe") || it.contains("type") }
        val catIndex = headers.indexOfFirst { it.contains("kategori") || it.contains("category") }
        val dateIndex = headers.indexOfFirst { it.contains("tanggal") || it.contains("date") }
        val notesIndex = headers.indexOfFirst { it.contains("catatan") || it.contains("notes") }
        
        if (descIndex == -1 || amountIndex == -1 || typeIndex == -1 || catIndex == -1) {
            throw IllegalArgumentException("Header CSV harus mengandung kolom Deskripsi, Jumlah, Tipe, dan Kategori.")
        }
        
        val parsedList = mutableListOf<FinancialRecord>()
        var headerSkipped = false
        for (line in lines) {
            if (line.trim().isEmpty()) continue
            if (!headerSkipped) {
                headerSkipped = true
                continue
            }
            
            val columns = parseCsvLine(line)
            if (columns.size <= maxOf(descIndex, amountIndex, typeIndex, catIndex)) {
                continue
            }
            
            val rawDesc = columns[descIndex].trim()
            if (rawDesc.isEmpty()) continue
            
            val rawAmountStr = columns[amountIndex].trim()
            val amount = rawAmountStr.replace(",", ".").toDoubleOrNull()
            if (amount == null || amount <= 0 || amount.isNaN()) {
                throw IllegalArgumentException("Jumlah transaksi '$rawAmountStr' harus berupa angka positif.")
            }
            
            var type = columns[typeIndex].trim().lowercase()
            if (type.contains("pemasukan") || type.contains("income") || type == "masuk" || type == "+") {
                type = "income"
            } else if (type.contains("pengeluaran") || type.contains("expense") || type == "keluar" || type == "-") {
                type = "expense"
            } else {
                throw java.lang.IllegalArgumentException("Tipe transaksi '$type' harus 'Pemasukan' atau 'Pengeluaran'.")
            }
            
            val category = columns[catIndex].trim()
            if (category.isEmpty()) {
                throw java.lang.IllegalArgumentException("Kategori transaksi tidak boleh kosong.")
            }
            
            var date: Long = System.currentTimeMillis()
            if (dateIndex != -1 && dateIndex < columns.size) {
                val rawDateStr = columns[dateIndex].trim()
                if (rawDateStr.isNotEmpty()) {
                    val ts = rawDateStr.toLongOrNull()
                    if (ts != null) {
                        date = ts
                    } else {
                        val parsedDate = tryParseDate(rawDateStr)
                        if (parsedDate != null) {
                            date = parsedDate
                        }
                    }
                }
            }
            
            val notes = if (notesIndex != -1 && notesIndex < columns.size) columns[notesIndex].trim() else ""
            
            parsedList.add(
                FinancialRecord(
                    description = rawDesc,
                    amount = amount,
                    type = type,
                    category = category,
                    date = date,
                    notes = notes
                )
            )
        }
        
        if (parsedList.isEmpty()) {
            throw IllegalArgumentException("Tidak ada data transaksi valid yang ditemukan untuk diimpor.")
        }
        
        return parsedList
    }
}
