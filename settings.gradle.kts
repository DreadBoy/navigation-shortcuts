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

rootProject.name = "navigation-shortcuts"
include(":app")
// Black-box E2E suite (UI Automator against Niagara + Google Maps). Separate test module so it runs in its
// own process and can force-stop the app under test; see e2e/run-e2e.sh.
include(":e2e")
