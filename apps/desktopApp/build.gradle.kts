plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

dependencies {
    implementation(project(":shared:core:common"))
    implementation(project(":shared:core:network"))
    implementation(project(":shared:core:di"))
    implementation(project(":shared:core:ui"))
    implementation(project(":shared:feature:search"))
    implementation(project(":shared:feature:history"))
    implementation(libs.ktor.client.cio)
    implementation(libs.koin.core)
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
}

compose.desktop {
    application { mainClass = "kg.dev.apps.desktop.MainKt" }
}
