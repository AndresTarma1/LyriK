plugins {
    alias(libs.plugins.kotlin.serialization)
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.encoding)
    implementation(libs.brotli)
    implementation("com.github.MetrolistGroup:MetrolistExtractor:6305155") {
        exclude(group = "com.google.protobuf")
    }
    // Upstream (Metrolist) uses Timber (Android-only) for logging; swapped for Napier since this
    // module has no Android dependency and the rest of LyriK already logs through Napier.
    implementation("io.github.aakira:napier:2.7.1")
    testImplementation(libs.junit)
}
