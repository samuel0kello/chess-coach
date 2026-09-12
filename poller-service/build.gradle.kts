plugins {
    id("co.chesscoach.kotlin-jvm")
    id("co.chesscoach.koin")
    application
}

application {
    mainClass.set("co.chesscoach.poller.PollerApplicationKt")
}

dependencies {
    implementation(project(":domain"))
}
