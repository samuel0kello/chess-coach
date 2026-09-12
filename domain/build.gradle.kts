plugins {
    id("co.chesscoach.kotlin-jvm")
    id("co.chesscoach.kotlin-serialization")
}

dependencies {
    api(libs.exposed.core)
    api(libs.exposed.java.time)
    api(libs.exposed.jdbc)
    api(libs.postgresql)
    api(libs.kotlinx.serialization.json)
    api(libs.rabbitmq)
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.postgres)
}
