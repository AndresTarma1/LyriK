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
    "-Dkotlinx.coroutines.io.parallelism=16",
    "-XX:MaxMetaspaceSize=192m",
    "-XX:ReservedCodeCacheSize=128m",
    "-Dskiko.gpu.resourceCacheLimit=67108864",
)

val melodistDevJvmArgs = melodistJvmArgs

tasks.withType<JavaExec>().configureEach {
    if (name.contains("run", ignoreCase = true)) {
        jvmArgs(*melodistDevJvmArgs.toTypedArray())
    }
}


afterEvaluate {
    tasks.withType<JavaExec>().configureEach {
        if (name.contains("run", ignoreCase = true)) {
            val jbrLauncher = javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(21))
                vendor.set(JvmVendorSpec.JETBRAINS)
            }
            executable(jbrLauncher.get().executablePath.asFile.absolutePath)
        }
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

            implementation(libs.coil.compose.get().toString()) {
                exclude(group = "org.jetbrains.skiko")
            }
            implementation(libs.coil.network.ktor3.get().toString()) {
                exclude(group = "org.jetbrains.skiko")
            }

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

                implementation(libs.autolaunch)

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

        jvmArgs(*melodistJvmArgs.toTypedArray())

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb)
            packageName = "LyriK"
            packageVersion = "0.4.0"

            windows {
                msiPackageVersion = "0.4.0"
                packageName = "LyriK"
                iconFile.set(project.file("icons/Music_note_circle.ico"))
                menu = true
                menuGroup = "LyriK"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "4A2F8B6C-1D3E-4F5A-B7C8-9D0E1F2A3B4C"
            }
            linux {
                // los nombres de paquete .deb deben ser en minúsculas. Linux usa el mpv + yt-dlp del sistema
                // (no empaquetados) — ver el README para las dependencias de tiempo de ejecución a instalar.
                packageName = "lyrik"
                debMaintainer = "andrestormenta1@gmail.com"
                menuGroup = "AudioVideo"
                appCategory = "Audio"
                iconFile.set(project.file("icons/music.png"))
                shortcut = true
            }
            vendor = "Tarma"
            description = "Reproductor de música de escritorio"

            includeAllModules = true

            // Los recursos nativos empaquetados (libmpv/yt-dlp) solo existen para Windows; en Linux usamos el
            // mpv instalado en el sistema, así que no hay nada que empaquetar desde aquí.
            appResourcesRootDir.set(project.layout.projectDirectory.dir("../mpv-resources"))
        }
    }
}
