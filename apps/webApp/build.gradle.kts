plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    js("web", IR) {
        browser()
        binaries.executable()
    }
    sourceSets {
        getByName("webMain").dependencies {
            implementation(project(":shared:core:common"))
            implementation(project(":shared:core:network"))
            implementation(project(":shared:core:di"))
            implementation(project(":shared:core:ui"))
            implementation(project(":shared:feature:search"))
            implementation(libs.ktor.client.js)
            implementation(libs.koin.core)
            implementation(compose.runtime)
            implementation(compose.material3)
            implementation(compose.ui)
        }
    }
}
