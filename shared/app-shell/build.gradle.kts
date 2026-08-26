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
        implementation(project(":shared:feature:home"))
        implementation(project(":shared:feature:history"))
        implementation(project(":shared:feature:player"))
        implementation(project(":shared:feature:search"))
        implementation(libs.decompose)
        implementation(libs.decompose.compose)
        implementation(compose.runtime)
        implementation(compose.foundation)
        implementation(compose.material3)
        implementation(compose.materialIconsExtended)
        implementation(compose.components.uiToolingPreview)
    }
    sourceSets.commonTest.dependencies {
        implementation(kotlin("test"))
        implementation(libs.coroutines.test)
    }
}

android {
    namespace = "kg.dev.shared.appshell"
    compileSdk = 35
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
