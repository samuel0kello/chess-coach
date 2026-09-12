
pluginManagement {
    includeBuild("buildLogic")
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "chess_coach_backend"
include("domain")

include("api-service")
include("poller-service")

include("worker-service")
