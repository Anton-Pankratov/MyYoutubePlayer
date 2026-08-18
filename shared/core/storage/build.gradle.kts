import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.sqldelight)
    id("com.android.library")
}

kotlin {
    androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    jvm("desktop")
    iosArm64()
    iosSimulatorArm64()
    iosX64()
    js("web", IR) { browser() }

    sourceSets {
        commonMain.dependencies { api(libs.sqldelight.runtime) }
        androidMain.dependencies { implementation(libs.sqldelight.android.driver) }
        getByName("desktopMain").dependencies { implementation(libs.sqldelight.sqlite.driver) }
        iosMain.dependencies { implementation(libs.sqldelight.native.driver) }
        getByName("webMain").dependencies { implementation(libs.sqldelight.web.worker.driver) }
    }
}

android {
    namespace = "kg.dev.shared.core.storage"
    compileSdk = 35
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

sqldelight {
    databases {
        create("PlayerDatabase") {
            packageName.set("kg.dev.shared.core.storage.db")
        }
    }
}
