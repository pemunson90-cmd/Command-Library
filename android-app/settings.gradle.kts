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
include(":core:model", ":core:common", ":data:storage", ":data:network", ":data:vault", ":feature:settings", ":feature:catalog", ":feature:shell", ":feature:rules", ":feature:life", ":feature:search", ":feature:decks", ":feature:links", ":feature:pricing", ":feature:collection", ":feature:transfer", ":feature:validation", ":feature:ai", ":app")
