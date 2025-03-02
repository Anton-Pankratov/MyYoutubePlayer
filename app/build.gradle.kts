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
}


dependencies {
    implementation(project(":common-events"))
    implementation(project(":common-logger"))
    implementation(project(":common-mapper"))
    implementation(project(":common-network-api"))
    implementation(project(":common-network-client"))
    implementation(project(":common-network-services"))
    implementation(project(":common-paging"))
    implementation(project(":common-usecase"))
    implementation(project(":common-viewmodel"))
    implementation(project(":common-utils"))
    implementation(project(":core-repositories"))

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

    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.coil.network)

    implementation(libs.youtube.player)
    implementation(libs.youtube.google.api.service)
    implementation(libs.youtube.google.http.client.android)
    implementation(libs.youtube.google.http.client.gson)

    implementation(libs.koin)
    implementation(libs.koin.compose)
    implementation(libs.navigation.compose)

    implementation(libs.paging)
    implementation(libs.paging.compose)

    implementation(libs.timber)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
