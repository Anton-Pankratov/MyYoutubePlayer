plugins {
    id("com.android.library")
    id("build.gradle.plugin.android.library")
    kotlin("kapt")
}

android {
    namespace = "kg.dev.core.repositories"
}

dependencies {

    implementation(project(":common-network-api"))
    implementation(project(":common-network-services"))
    implementation(project(":common-mapper"))

    implementation(libs.koin)
    implementation(libs.gson)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}