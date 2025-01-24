import java.util.Properties

plugins {
    id("com.android.library")
    id("build.gradle.plugin.android.library")
    kotlin("kapt")
}

android {
    namespace = "kg.dev.common.network.api"

    defaultConfig {
        val apiProperties = Properties().apply {
            load(File(rootDir, "app_credentials.properties").inputStream())
        }
        val baseUrl = apiProperties["YOUTUBE_API_URL"] as String
        val apiKey = apiProperties["YOUTUBE_DATA_API_V3_API_KEY"] as String

        buildConfigField("String", "YOUTUBE_API_URL", baseUrl)
        buildConfigField("String", "YOUTUBE_API_KEY", apiKey)
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}