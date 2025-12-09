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
        // project-specific mirrors (put first to override dl.google.com)
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.rokid.com/repository/maven-public/") }

        // official repositories
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "CXRMSamples"
include(":app")
