import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    id("com.android.library")
}

kotlin {
    androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    jvm("desktop")
    iosArm64()
    iosSimulatorArm64()
    iosX64()
    js("web", IR) { browser() }

    sourceSets.commonMain.dependencies {
        implementation(project(":shared:core:common"))
        implementation(project(":shared:core:ui"))
        implementation(project(":shared:feature:history"))
        api(libs.coroutines)
        implementation(libs.decompose)
        implementation(compose.runtime)
        implementation(compose.foundation)
        implementation(compose.material3)
        implementation(libs.koin.core)
    }
    sourceSets.androidMain.dependencies {
        implementation(libs.media3.exoplayer)
        implementation(libs.media3.ui)
    }
    sourceSets.commonTest.dependencies {
        implementation(kotlin("test"))
        implementation(libs.coroutines.test)
    }
    sourceSets.getByName("desktopTest").dependencies {
        implementation(libs.sqldelight.sqlite.driver)
    }
}

android {
    namespace = "kg.dev.shared.feature.player"
    compileSdk = 35
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
