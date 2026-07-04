package com.example.melodist.platform

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

actual object CsvFilePicker {
    actual suspend fun pickAndReadCsvFile(): CsvFileResult? {
        val dialog = FileDialog(null as Frame?, "Importar CSV", FileDialog.LOAD)
        dialog.file = "*.csv"
        dialog.isVisible = true
        val dir = dialog.directory
        val file = dialog.file
        if (dir != null && file != null) {
            val csvFile = File(dir, file)
            return CsvFileResult(
                fileName = csvFile.nameWithoutExtension,
                content = csvFile.readText(),
            )
        }
        return null
    }
}
