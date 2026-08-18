pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    // Kotlin/JS registers its Node distribution as an Ivy project repository.
    // PREFER_PROJECT keeps that toolchain repository available while dependency
    // coordinates remain centralized in the version catalog below.
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MyYoutubePlayer"

include(":app")
include(":shared:core:common")
include(":shared:core:network")
include(":shared:core:storage")
include(":shared:core:ui")
include(":shared:core:di")
include(":shared:feature:search")
include(":shared:feature:player")
include(":shared:feature:history")
include(":apps:desktopApp")
include(":apps:webApp")
include(":apps:iosApp")
