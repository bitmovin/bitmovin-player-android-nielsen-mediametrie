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
        maven {
            url = uri("https://artifacts.bitmovin.com/artifactory/public-releases")
        }
        maven {
            url = uri("https://raw.githubusercontent.com/NielsenDigitalSDK/nielsenappsdk-android/master/")
        }
        maven {
            url = uri("build/maven_repo")
        }
    }
}

rootProject.name = "NielsenBitmovinTestApp"
include(":app")
include(":nielsen-mediametrie-sdk")
