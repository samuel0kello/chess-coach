import extension.catalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
}

group = "co.chesscoach"
version = catalog.findVersion("project").get().requiredVersion

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(
        catalog
            .findVersion("java-toolchain")
            .get()
            .requiredVersion
            .toInt(),
    )
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(catalog.findVersion("java-target").get().requiredVersion)
    }
}

java {
    sourceCompatibility = JavaVersion.toVersion(catalog.findVersion("java-target").get().requiredVersion)
    targetCompatibility = JavaVersion.toVersion(catalog.findVersion("java-target").get().requiredVersion)
}

tasks.test {
    useJUnitPlatform()
}
