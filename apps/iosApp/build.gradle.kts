plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "IosApp"
            isStatic = true
        }
    }
    sourceSets {
        iosMain.dependencies {
            implementation(project(":shared:core:common"))
            implementation(project(":shared:core:network"))
            implementation(project(":shared:core:di"))
            implementation(project(":shared:core:ui"))
            implementation(project(":shared:app-shell"))
            implementation(project(":shared:feature:home"))
            implementation(project(":shared:feature:search"))
            implementation(project(":shared:feature:history"))
            implementation(project(":shared:feature:player"))
            implementation(libs.ktor.client.darwin)
            implementation(libs.koin.core)
            implementation(compose.runtime)
            implementation(compose.material3)
            implementation(compose.ui)
        }
    }
}
