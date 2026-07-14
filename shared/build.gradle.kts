plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm()
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            // coloca aquí tus dependencias Multiplatform
            api(project(":innertube"))
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.serialization.protobuf)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.ktor.client.websockets)

            api(libs.sqldelight.coroutines)


            api("io.github.aakira:napier:2.7.1")



            // Librería DataStore
            api("androidx.datastore:datastore:1.2.1")
            api("androidx.datastore:datastore-preferences:1.2.1")

        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            api(libs.sqldelight.driver.jvm)
            // Proveedores de letras (LRC sincronizado) — módulos solo JVM
            implementation(project(":lrclib"))
            implementation(project(":kugou"))
            // Fuente: https://mvnrepository.com/artifact/net.java.dev.jna/jna
            implementation("net.java.dev.jna:jna:5.18.1")

            // Fuente: https://mvnrepository.com/artifact/net.java.dev.jna/jna-platform-jpms
            implementation("net.java.dev.jna:jna-platform-jpms:5.18.1")
            implementation("org.jetbrains.runtime:jbr-api:1.10.1")
            implementation("dev.toastbits:mediasession:0.1.1")

            // Fuente: https://mvnrepository.com/artifact/org.graalvm.js/js-scriptengine
            implementation("org.graalvm.js:js-scriptengine:25.0.3")

            // Fuente: https://mvnrepository.com/artifact/org.graalvm.js/js
            implementation("org.graalvm.js:js:25.0.3")

            // API de JCEF (Chromium) solo — usada para generar poTokens de BotGuard. La implementación
            // nativa real es proporcionada en tiempo de ejecución por el módulo `jcef` de JetBrains Runtime
            // (ver --add-modules=jcef en composeApp), así que esto se mantiene compileOnly para evitar
            // enviar/binarios nativos en conflicto.
            compileOnly("me.friwi:jcef-api:jcef-1770317+cef-132.3.1+g144febe+chromium-132.0.6834.83")
        }
    }
}

sqldelight {
    databases {
        create("MelodistDatabase") {
            packageName.set("com.example.melodist.db")
        }
    }
}
