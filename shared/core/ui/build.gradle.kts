import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
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
        api(libs.decompose)
        api(libs.decompose.compose)
        implementation(libs.kotlinx.serialization.json)
    }
}

android {
    namespace = "kg.dev.shared.core.ui"
    compileSdk = 35
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
