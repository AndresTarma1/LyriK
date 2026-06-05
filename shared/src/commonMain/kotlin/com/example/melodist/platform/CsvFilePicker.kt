package com.example.melodist.platform

data class CsvFileResult(
    val fileName: String,
    val content: String,
)

expect object CsvFilePicker {
    suspend fun pickAndReadCsvFile(): CsvFileResult?
}
