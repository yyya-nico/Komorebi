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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("${rootDir}/local_repo") }
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Komorebi"
include(":app")
// settings.gradle.kts (Kotlin DSL 形式)
//include(":media-decoder-ffmpeg")
//project(":media-decoder-ffmpeg").projectDir = File("/Users/taichimaekawa/Documents/ffmpeg/media/libraries/decoder_ffmpeg")
include(":baselineprofile")
