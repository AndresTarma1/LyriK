package com.example.melodist.ui.screens.shared

import java.awt.Desktop
import java.io.File

fun openFolder(folder: File) {
    try {
        if (!folder.exists()) folder.mkdirs()

        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().open(folder)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}