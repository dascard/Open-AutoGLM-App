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
        // Shizuku (dev.rikka)
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Open-AutoGLM-App"
include(":app")
