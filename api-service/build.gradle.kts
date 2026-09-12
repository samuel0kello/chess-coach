plugins {
    id("co.chesscoach.vertx-application")
    id("co.chesscoach.koin")
}

dependencies {
    implementation(project(":domain"))
    implementation(libs.rabbitmq)
    runtimeOnly("io.netty:netty-resolver-dns-native-macos:${libs.versions.netty.get()}:osx-aarch_64")
    runtimeOnly("io.netty:netty-resolver-dns-native-macos:${libs.versions.netty.get()}:osx-x86_64")
}

tasks.named<JavaExec>("run") {
    environment("APP_ENV", providers.environmentVariable("APP_ENV").orElse("dev").get())
    environment(
        "DATABASE_URL",
        providers.environmentVariable("DATABASE_URL")
            .orElse("jdbc:postgresql://localhost:5432/chesscoach")
            .get()
    )
    environment("DATABASE_USER", providers.environmentVariable("DATABASE_USER").orElse("chesscoach").get())
    environment(
        "DATABASE_PASSWORD",
        providers.environmentVariable("DATABASE_PASSWORD").orElse("chesscoach-dev-password").get()
    )
    environment(
        "JWT_SECRET",
        providers.environmentVariable("JWT_SECRET")
            .orElse("chesscoach-development-jwt-secret-change-for-production")
            .get()
    )
    environment("RABBITMQ_HOST", providers.environmentVariable("RABBITMQ_HOST").orElse("localhost").get())
    environment("RABBITMQ_PORT", providers.environmentVariable("RABBITMQ_PORT").orElse("5672").get())
    environment("RABBITMQ_USER", providers.environmentVariable("RABBITMQ_USER").orElse("chesscoach").get())
    environment("RABBITMQ_PASSWORD", providers.environmentVariable("RABBITMQ_PASSWORD").orElse("rabbitmq-dev-password").get())
    environment("RABBITMQ_QUEUE", providers.environmentVariable("RABBITMQ_QUEUE").orElse("analysis.jobs").get())
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
