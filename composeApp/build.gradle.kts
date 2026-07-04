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
    // Cap the coroutine IO pool: blocking IO bursts (stream resolve, downloads, lyrics providers,
    // thumbnails) were spawning ~58 "DefaultDispatcher-worker" threads (default limit 64). 16 is
    // plenty for a player and keeps thread stacks / scheduler overhead down.
    "-Dkotlinx.coroutines.io.parallelism=16",
    // Cap metaspace (≈100 MB in use from Compose/Skiko/libs) so it can't creep, and shrink the
    // JIT code-cache reservation (≈46 MB used of a 240 MB default reservation).
    "-XX:MaxMetaspaceSize=192m",
    "-XX:ReservedCodeCacheSize=128m",
    "-Dskiko.gpu.resourceCacheLimit=67108864",
)

val melodistDevJvmArgs = melodistJvmArgs

// We run/package on the plain JetBrains Runtime (the toolchain JBR). The poToken path (JCEF) is
// disabled, so the jcef module is no longer required at runtime; the poToken sources still compile
// because jcef-api is a compileOnly dependency in :shared.
tasks.withType<JavaExec>().configureEach {
    if (name.contains("run", ignoreCase = true)) {
        jvmArgs(*melodistDevJvmArgs.toTypedArray())
    }
}

kotlin {
    jvm("desktop")

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

            // Coil pulls an old transitive Skiko (0.9.x) that clashes with Compose's (0.144.x) and
            // triggers checkDesktopMainComposeLibrariesCompatibility. Drop it; Compose provides Skiko.
            // (Version-catalog accessors don't take an exclude lambda directly, hence get().toString().)
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
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs){
                    exclude(group = "org.jetbrains.compose.material")
                }
                implementation(libs.kotlinx.coroutinesSwing)
                implementation(libs.jnativehook)

                implementation(libs.jna)
                implementation(libs.jna.platform.jpms)

                // Launch-at-Windows-startup (registry Run key). vinceglb/AutoLaunch.
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

        // Package with the toolchain's plain JBR (no JCEF). javaHome is left unset so Compose uses it.
        jvmArgs(*melodistJvmArgs.toTypedArray())

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "LyriK"
            packageVersion = "0.3.0"

            windows {
                msiPackageVersion = "0.3.0"
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
