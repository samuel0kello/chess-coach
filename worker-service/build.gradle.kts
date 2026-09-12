plugins {
    id("co.chesscoach.kotlin-jvm")
    id("co.chesscoach.koin")
    application
}

application {
    mainClass.set("co.chesscoach.worker.WorkerApplicationKt")
}

dependencies {
    implementation(project(":domain"))
}
