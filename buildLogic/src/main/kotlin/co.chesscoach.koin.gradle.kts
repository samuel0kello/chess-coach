import extension.catalog
import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("co.chesscoach.kotlin-jvm")
}

dependencies {
    implementation(catalog.findLibrary("koin-core").get())
    testImplementation(catalog.findLibrary("koin-test").get())
}
