plugins {
    id("com.android.library")
    id("build.gradle.plugin.android.library")
}

android {
    namespace = "kg.dev.common.usecase"
}

dependencies {

    api(libs.coroutines)

    testImplementation(libs.junit)
}