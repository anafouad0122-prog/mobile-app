// Maven Central's own hosts rate-limit a parallel Gradle resolve with HTTP 429 from some
// networks, so Google's read-only Central mirror is tried first and Central is kept as the
// fallback. Both serve identical artifacts; drop the mirror lines if it ever goes away.
pluginManagement {
    repositories {
        google()
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
}

rootProject.name = "CuttingNotebook"
include(":app")
