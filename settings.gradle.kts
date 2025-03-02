import java.util.Properties

pluginManagement {
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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MyYoutubePlayer"

include(":app")
include(":common-async")
include(":common-network-client")
include(":common-network-services")
include(":common-network-api")
include(":common-logger")
include(":common-mapper")
include(":common-usecase")
include(":common-paging")
include(":common-events")
include(":common-utils")
include(":common-viewmodel")
include(":core-repositories")

val properties = Properties()
file("local.properties").inputStream().use { properties.load(it) }

val youtubeBaseUrl = properties.getProperty("youtubeBaseUrl", "default")
