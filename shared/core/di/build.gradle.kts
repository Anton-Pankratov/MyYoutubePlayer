import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
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
        api(libs.koin.core)
        implementation(project(":shared:core:network"))
        implementation(project(":shared:core:storage"))
        implementation(project(":shared:feature:search"))
        implementation(project(":shared:feature:history"))
    }
}

android {
    namespace = "kg.dev.shared.core.di"
    compileSdk = 35
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
