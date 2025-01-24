import com.android.build.gradle.AppExtension
import com.android.build.gradle.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

class BuildGradlePluginConfig : Plugin<Project> {
    override fun apply(target: Project) {
        target.plugins.apply("org.jetbrains.kotlin.android")

        when {
            target.plugins.hasPlugin("com.android.application") -> {
                target.plugins.apply("com.android.application")
                target.extensions.configure<AppExtension> {
                    setCompileSdkVersion(35)

                    defaultConfig {
                        minSdk = 24
                        targetSdk = 35
                        versionCode = 1
                        versionName = "1.0"
                        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                    }

                    buildTypes {
                        getByName("release") {
                            isMinifyEnabled = false
                            proguardFiles(
                                getDefaultProguardFile("proguard-android-optimize.txt"),
                                "proguard-rules.pro"
                            )
                        }
                    }

                    target.tasks.withType<KotlinJvmCompile>().configureEach {
                        compilerOptions {
                            jvmTarget.set(JvmTarget.JVM_17)
                        }
                    }

                    compileOptions {
                        sourceCompatibility = JavaVersion.VERSION_17
                        targetCompatibility = JavaVersion.VERSION_17
                    }
                }
            }

            target.plugins.hasPlugin("com.android.library") -> {
                target.plugins.apply("com.android.library")
                target.extensions.configure<LibraryExtension> {
                    compileSdk = 35

                    defaultConfig {
                        minSdk = 24
                        lint.targetSdk = 35
                        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                        consumerProguardFiles("consumer-rules.pro")
                    }

                    buildTypes {
                        getByName("release") {
                            isMinifyEnabled = false
                            proguardFiles(
                                getDefaultProguardFile("proguard-android-optimize.txt"),
                                "proguard-rules.pro"
                            )
                        }
                    }

                    target.tasks.withType<KotlinJvmCompile>().configureEach {
                        compilerOptions {
                            jvmTarget.set(JvmTarget.JVM_17)
                        }
                    }

                    compileOptions {
                        sourceCompatibility = JavaVersion.VERSION_17
                        targetCompatibility = JavaVersion.VERSION_17
                    }
                }
            }
        }
    }
}