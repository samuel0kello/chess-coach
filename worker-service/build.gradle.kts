plugins {
    id("co.chesscoach.kotlin-jvm")
    id("co.chesscoach.koin")
    application
}

application {
    mainClass.set("co.chesscoach.worker.WorkerApplicationKt")
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation(project(":domain"))
}
