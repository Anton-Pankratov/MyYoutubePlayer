import java.util.Properties

plugins {
    id("com.android.library")
    id("build.gradle.plugin.android.library")
    kotlin("kapt")
}

android {
    namespace = "kg.dev.common.network.services"

    defaultConfig {
        val apiProperties = Properties().apply {
            load(File(rootDir, "app_credentials.properties").inputStream())
        }

        val apiKey = apiProperties["YOUTUBE_DATA_API_V3_API_KEY"] as String
        val youtubeUrl = apiProperties["YOUTUBE_API_URL"] as String

        buildConfigField("String", "YOUTUBE_API_KEY", apiKey)
        buildConfigField("String", "YOUTUBE_API_URL", youtubeUrl)
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {

    implementation(libs.koin)

    api(libs.retrofit)
    api(libs.retrofit.converter.gson)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}