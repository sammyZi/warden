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

rootProject.name = "warden"

include(
    ":app",
    ":core-engine",
    ":core-rules",
    ":core-design",
    ":feature-dashboard",
    ":feature-apps",
    ":feature-map",
    ":feature-logs",
    ":feature-settings",
)
