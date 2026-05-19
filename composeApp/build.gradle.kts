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
)

val melodistDevJvmArgs = melodistJvmArgs

tasks.withType<JavaExec>().configureEach {
    if (name.contains("run", ignoreCase = true)) {
        jvmArgs(*melodistDevJvmArgs.toTypedArray())
    }
}

kotlin {
    jvm()

    jvmToolchain(21) // Esto fuerza a Gradle a buscar/descargar un JDK completo (v21)


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
//            implementation(libs.heze.materials)

            implementation("ir.mahozad.multiplatform:wavy-slider:2.2.0")

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

        jvmArgs(*melodistJvmArgs.toTypedArray())

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Melodist"
            packageVersion = "0.1.3"

            windows {
                msiPackageVersion = "0.1.3"
                packageName = "Melodist"
                iconFile.set(project.file("icons/music.ico"))
                menu = true
                menuGroup = "Melodist"
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
