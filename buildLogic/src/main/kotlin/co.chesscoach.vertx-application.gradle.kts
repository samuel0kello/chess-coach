import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import extension.catalog
import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
import org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("co.chesscoach.kotlin-jvm")
    application
    id("com.gradleup.shadow")
}

application {
    mainClass.set("io.vertx.launcher.application.VertxApplication")
}

dependencies {
    implementation(platform(catalog.findLibrary("vertx-stack-depchain").get()))
    implementation(catalog.findLibrary("vertx-launcher-application").get())
    implementation(catalog.findLibrary("vertx-auth-jwt").get())
    implementation(catalog.findLibrary("vertx-web").get())
    implementation(catalog.findLibrary("vertx-pg-client").get())
    implementation(catalog.findLibrary("vertx-lang-kotlin-coroutines").get())
    implementation(catalog.findLibrary("vertx-lang-kotlin").get())
    testImplementation(catalog.findLibrary("vertx-junit5").get())
    testImplementation(catalog.findLibrary("junit-jupiter").get())
    testRuntimeOnly(catalog.findLibrary("junit-platform-launcher").get())
}

kotlin {
    compilerOptions {
        languageVersion = KotlinVersion.fromVersion(catalog.findVersion("kotlin-language").get().requiredVersion)
        apiVersion = KotlinVersion.fromVersion(catalog.findVersion("kotlin-language").get().requiredVersion)
    }
}

java {
    sourceCompatibility = JavaVersion.toVersion(catalog.findVersion("java-target").get().requiredVersion)
    targetCompatibility = JavaVersion.toVersion(catalog.findVersion("java-target").get().requiredVersion)
}

tasks.withType<ShadowJar> {
    archiveClassifier.set("fat")
    manifest {
        attributes("Main-Verticle" to "co.chesscoach.MainVerticle")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events = setOf(PASSED, SKIPPED, FAILED)
    }
}

tasks.withType<JavaExec> {
    standardInput = System.`in`
    args = listOf("co.chesscoach.MainVerticle")
}
