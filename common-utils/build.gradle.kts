plugins {
    id("com.android.library")
    id("build.gradle.plugin.android.library")
}

android {
    namespace = "kg.dev.common.utils"
}

dependencies {

    implementation(libs.gson)

    testImplementation(libs.junit)
}