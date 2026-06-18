import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlin.serialization)

}

val melodistJvmArgs = listOf(
    "--add-modules=java.sql",
    "--add-modules=jcef",
    "--enable-native-access=ALL-UNNAMED",
    "-Dorg.sqlite.tmpdir=${System.getProperty("user.home")}/.melodist/tmp",
    "-XX:+UseG1GC",
    "-Xmx512m",
    "-Xms64m",
    "-XX:MaxGCPauseMillis=80",
    "-XX:+UseStringDeduplication",
    "-XX:+UseCompressedOops",
    "-XX:MaxHeapFreeRatio=30",
    "-XX:MinHeapFreeRatio=10",
    "-Dskiko.gpu.resourceCacheLimit=67108864",
)

val melodistDevJvmArgs = melodistJvmArgs

/**
 * Locate a JetBrains Runtime that bundles JCEF (Chromium). PoToken generation
 * (BotGuard) runs inside JCEF, and only the `jbr_jcef` variant ships the `jcef` module
 * — the plain `jbr` variant does NOT, so `--add-modules=jcef` would fail there.
 * We can't express "must have jcef" via the toolchain spec, so we discover it on disk.
 */
fun findJcefCapableJbr(): File? {
    val userHome = File(System.getProperty("user.home"))
    val searchDirs = listOf(File(userHome, ".jdks"), File(userHome, ".gradle/jdks"))
    val candidates = searchDirs.flatMap { it.listFiles()?.toList() ?: emptyList() }
    return candidates.firstOrNull { dir ->
        dir.isDirectory && File(dir, "bin/java.exe").exists() &&
            (dir.name.contains("jcef", ignoreCase = true) ||
                File(dir, "bin/jcef_helper.exe").exists())
    }
}

val jcefJbrHome: File? = findJcefCapableJbr()
if (jcefJbrHome != null) {
    logger.lifecycle("Using JCEF-capable JBR for run/package: ${jcefJbrHome.absolutePath}")
} else {
    logger.warn("No JCEF-capable JBR (jbr_jcef) found under ~/.jdks — poToken generation will fail at runtime. Install a 'JetBrains Runtime with JCEF' build.")
}

tasks.withType<JavaExec>().configureEach {
    if (name.contains("run", ignoreCase = true)) {
        jvmArgs(*melodistDevJvmArgs.toTypedArray())
        jcefJbrHome?.let { executable = File(it, "bin/java.exe").absolutePath }
    }
}

kotlin {
    jvm()

    jvmToolchain {
        vendor = JvmVendorSpec.JETBRAINS
        languageVersion = JavaLanguageVersion.of(21)
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.materialIconsExtended)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(projects.shared)


            implementation(libs.compose.native.tray)

            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)

            implementation(libs.decompose)
            implementation(libs.decompose.compose)
            implementation(libs.kotlinx.serialization.core)

            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)

            implementation(libs.materialKolor)
            implementation(libs.kmpalette.core)
            implementation(libs.kmpalette.network)
            implementation(libs.kmpalette.extensions.file)


            implementation(libs.reorderable)

            implementation(libs.heze)
            implementation(libs.heze.blur)

            implementation("ir.mahozad.multiplatform:wavy-slider:2.2.0")

            implementation(libs.composeSettings.ui)
            implementation(libs.composeSettings.ui.expressive)
            implementation(libs.composeSettings.ui.extended)


        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain {
            dependencies {
                implementation(compose.desktop.currentOs){
                    exclude(group = "org.jetbrains.compose.material")
                }
                implementation(libs.kotlinx.coroutinesSwing)
                implementation(libs.jnativehook)

                implementation(libs.jna)
                implementation(libs.jna.platform.jpms)


                implementation(libs.jewel.ui.standalone)
                implementation(libs.jewel.ui.decorated.window)
                implementation(libs.jbr)

            }
        }
    }
}


compose.desktop {
    application {
        mainClass = "com.example.melodist.MainKt"

        // Package with the JCEF-capable JBR so the bundled runtime includes the jcef module.
        jcefJbrHome?.let { javaHome = it.absolutePath }

        jvmArgs(*melodistJvmArgs.toTypedArray())

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "LyriK"
            packageVersion = "0.1.4"

            windows {
                msiPackageVersion = "0.1.4"
                packageName = "LyriK"
                iconFile.set(project.file("icons/Music_note_circle.ico"))
                menu = true
                menuGroup = "LyriK"
                shortcut = true
                dirChooser = false
                perUserInstall = true
                upgradeUuid = "4A2F8B6C-1D3E-4F5A-B7C8-9D0E1F2A3B4C"
            }
            vendor = "Tarma"
            description = "Reproductor de música de escritorio"

            includeAllModules = true

            appResourcesRootDir.set(project.layout.projectDirectory.dir("../mpv-resources"))
        }
    }
}
