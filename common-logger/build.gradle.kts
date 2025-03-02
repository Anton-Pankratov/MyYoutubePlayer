plugins {
    id("com.android.library")
    id("build.gradle.plugin.android.library")
}

android {
    namespace = "kg.dev.common.logger"
}

dependencies {
    implementation(libs.koin)

    implementation(libs.timber)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}