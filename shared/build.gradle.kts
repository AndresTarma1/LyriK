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
            // put your Multiplatform dependencies here
            api(project(":innertube"))
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)

            api(libs.sqldelight.coroutines)


            api("io.github.aakira:napier:2.7.1")



            // DataStore library
            api("androidx.datastore:datastore:1.2.1")
            api("androidx.datastore:datastore-preferences:1.2.1")

        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            api(libs.sqldelight.driver.jvm)
            // Lyrics providers (synced LRC) — JVM-only modules
            implementation(project(":lrclib"))
            implementation(project(":kugou"))
            // Source: https://mvnrepository.com/artifact/net.java.dev.jna/jna
            implementation("net.java.dev.jna:jna:5.18.1")

            // Source: https://mvnrepository.com/artifact/net.java.dev.jna/jna-platform-jpms
            implementation("net.java.dev.jna:jna-platform-jpms:5.18.1")
            implementation("org.jetbrains.runtime:jbr-api:1.10.1")
            implementation("dev.toastbits:mediasession:0.1.1")

            // Source: https://mvnrepository.com/artifact/org.graalvm.js/js-scriptengine
            implementation("org.graalvm.js:js-scriptengine:25.0.3")

            // Source: https://mvnrepository.com/artifact/org.graalvm.js/js
            implementation("org.graalvm.js:js:25.0.3")

            // JCEF (Chromium) API only — used to mint BotGuard poTokens. The actual native
            // implementation is provided at runtime by the JetBrains Runtime's `jcef` module
            // (see --add-modules=jcef in composeApp), so this stays compileOnly to avoid
            // shipping/clashing native binaries.
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
