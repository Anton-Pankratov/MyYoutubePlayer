plugins {
    id("com.android.library")
    id("build.gradle.plugin.android.library")
}

android {
    namespace = "kg.dev.common.events"
}

dependencies {

    implementation(libs.koin)
}