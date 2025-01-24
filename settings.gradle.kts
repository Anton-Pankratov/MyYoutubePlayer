import java.util.Properties

include(":common-utils")


include(":core-repositories")


include(":core-repositories")


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
include(":common-network-client")
include(":common-network-services")
include(":common-network-api")

val properties = Properties()
file("local.properties").inputStream().use { properties.load(it) }

val youtubeBaseUrl = properties.getProperty("youtubeBaseUrl", "default")
