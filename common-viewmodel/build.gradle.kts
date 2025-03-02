plugins {
    id("com.android.library")
    id("build.gradle.plugin.android.library")
}

android {
    namespace = "kg.dev.common.viewmodel"
}

dependencies {

    api(libs.viewmodel)
    implementation(libs.koin)
}