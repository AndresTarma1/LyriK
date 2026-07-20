package com.example.musicApp.platform

/**
 * Central OS detection, so the scattered `System.getProperty("os.name")` checks live in one place
 * and every platform-specific branch reads the same way.
 */
enum class OperatingSystem { WINDOWS, LINUX, MAC, OTHER }

object Platform {
    val current: OperatingSystem by lazy {
        val name = System.getProperty("os.name").orEmpty().lowercase()
        when {
            name.contains("win") -> OperatingSystem.WINDOWS
            name.contains("linux") || name.contains("nix") || name.contains("nux") -> OperatingSystem.LINUX
            name.contains("mac") || name.contains("darwin") -> OperatingSystem.MAC
            else -> OperatingSystem.OTHER
        }
    }

    val isWindows: Boolean get() = current == OperatingSystem.WINDOWS
    val isLinux: Boolean get() = current == OperatingSystem.LINUX
    val isMac: Boolean get() = current == OperatingSystem.MAC
}
