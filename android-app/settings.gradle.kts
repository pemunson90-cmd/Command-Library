pluginManagement {
    repositories {
        google()
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

rootProject.name = "CommandLibrary"
include(":core:model", ":core:common", ":data:storage", ":data:network", ":feature:settings", ":feature:catalog", ":feature:shell", ":feature:rules", ":feature:life", ":feature:search", ":feature:decks", ":feature:links", ":app")
