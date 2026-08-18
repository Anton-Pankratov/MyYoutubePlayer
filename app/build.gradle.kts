import java.util.Properties

plugins {
    id("com.android.application")
    alias(libs.plugins.kotlin.compose)
    id("build.gradle.plugin.android.library")
    kotlin("kapt")
}

android {
    namespace = "kg.dev.videoplayer"

    buildFeatures {
        compose = true
        buildConfig = true
    }

    defaultConfig {
        val apiProperties = Properties()
        rootProject.file("app_credentials.properties").takeIf { it.isFile }?.inputStream()?.use(apiProperties::load)
        val youtubeApiKey = providers.environmentVariable("YOUTUBE_DATA_API_V3_API_KEY").orNull
            ?: apiProperties.getProperty("YOUTUBE_DATA_API_V3_API_KEY").orEmpty()
        buildConfigField(
            "String",
            "YOUTUBE_API_KEY",
            "\"${youtubeApiKey.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        )
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    packaging {
        resources.excludes.add("META-INF/DEPENDENCIES")
        resources.excludes.add("META-INF/LICENSE")
        resources.excludes.add("META-INF/LICENSE.txt")
        resources.excludes.add("META-INF/NOTICE")
        resources.excludes.add("META-INF/NOTICE.txt")
    }

    sourceSets["main"].java.setSrcDirs(listOf("src/main/kotlin-active"))
}


dependencies {
    implementation(project(":shared:core:common"))
    implementation(project(":shared:core:network"))
    implementation(project(":shared:core:storage"))
    implementation(project(":shared:core:di"))
    implementation(project(":shared:core:ui"))
    implementation(project(":shared:feature:search"))
    implementation(project(":shared:feature:player"))
    implementation(project(":shared:feature:history"))
    implementation(libs.sqldelight.android.driver)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material)
    implementation(libs.androidx.material.android)

    implementation(libs.koin)
    implementation(libs.koin.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
