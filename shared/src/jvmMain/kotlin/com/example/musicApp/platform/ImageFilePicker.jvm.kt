package com.example.musicApp.platform

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.imageio.ImageIO

actual object ImageFilePicker {
    actual suspend fun pickImageFile(): ImageFileResult? {
        val dialog = FileDialog(null as Frame?, "Seleccionar imagen", FileDialog.LOAD)
        dialog.file = "*.jpg;*.jpeg;*.png"
        dialog.isVisible = true
        val dir = dialog.directory
        val file = dialog.file
        if (dir != null && file != null) {
            val imageFile = File(dir, file)
            // Validate it's a readable image
            val image = ImageIO.read(imageFile) ?: return null
            if (image.width < 320 || image.height < 320) return null
            return ImageFileResult(
                fileName = imageFile.nameWithoutExtension,
                bytes = imageFile.readBytes(),
            )
        }
        return null
    }
}
