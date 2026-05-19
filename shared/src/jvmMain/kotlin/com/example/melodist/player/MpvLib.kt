package com.example.melodist.player

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.io.File
import java.util.logging.Logger

interface MpvLib : Library {
    fun mpv_create(): Pointer?
    fun mpv_initialize(handle: Pointer): Int
    fun mpv_command(handle: Pointer, args: Array<String?>): Int
    fun mpv_terminate_destroy(handle: Pointer)
    fun mpv_set_property_string(handle: Pointer, name: String, value: String): Int
    fun mpv_get_property_string(handle: Pointer, name: String): Pointer
    fun mpv_free(data: Pointer)

    companion object {
        private val log = Logger.getLogger("MpvLib")

        val INSTANCE: MpvLib by lazy {
            val userDir = File(System.getProperty("user.dir"))
            val rootDir = userDir.parentFile ?: userDir
            val resProp = System.getProperty("compose.application.resources.dir")
            val possibleDirs = mutableListOf(
                File(userDir, "resources"),
                File(userDir, "app/resources"),
                File(userDir, "app/app/resources"),
                File(userDir, "mpv-resources/windows"),
                File(userDir, "mpv-resources"),
                File(rootDir, "mpv-resources/windows"),
                File(rootDir, "mpv-resources"),
                File(rootDir, "resources"),
            )
            if (resProp != null) {
                possibleDirs.add(File(resProp))
                possibleDirs.add(File(resProp, "windows"))
            }

            val libraryDir = possibleDirs.firstOrNull { File(it, "libmpv-2.dll").exists() }
            if (libraryDir != null) {
                System.setProperty("jna.library.path", libraryDir.absolutePath)
                log.info("MpvLib: cargando libmpv desde ${libraryDir.absolutePath}")
            } else {
                log.warning(
                    "MpvLib: no se encontró libmpv-2.dll en: ${possibleDirs.joinToString { it.absolutePath }}"
                )
            }

            Native.load("libmpv-2", MpvLib::class.java)
        }
    }
}
