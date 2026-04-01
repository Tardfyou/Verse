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

rootProject.name = "PaperLens"

include(
    ":app",
    ":core:common",
    ":core:ui",
    ":core:designsystem",
    ":core:model",
    ":core:database",
    ":core:datastore",
    ":core:file",
    ":core:ml",
    ":core:tts",
    ":core:testing",
    ":feature:home",
    ":feature:camera",
    ":feature:ocr",
    ":feature:reader",
    ":feature:pdf",
    ":feature:highlights",
    ":feature:settings",
    ":baselineprofile",
    ":benchmark",
)
