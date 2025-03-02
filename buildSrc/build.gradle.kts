plugins {
    `java-gradle-plugin`
    `kotlin-dsl` // Подключает поддержку Kotlin для Gradle-скриптов
}

repositories {
    mavenCentral()
    google() // Если нужны зависимости для Android
    gradlePluginPortal()
}

gradlePlugin {
    plugins {
        create("buildGradlePlugin") {
            id = "build.gradle.plugin.android.library"
            implementationClass = "BuildGradlePluginConfig"
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.0")
    implementation("com.android.tools.build:gradle:8.7.2")
    implementation("org.tomlj:tomlj:1.0.0")
}