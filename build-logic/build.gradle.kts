plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

gradlePlugin {
    plugins {
        create("androidConfiguration") {
            id = "build.gradle.plugin.android.library"
            implementationClass = "BuildGradlePluginConfig"
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")
    implementation("com.android.tools.build:gradle:8.7.2")
}
