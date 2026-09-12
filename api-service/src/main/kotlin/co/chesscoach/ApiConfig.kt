package co.chesscoach

data class ApiConfig(
    val environment: String = env("APP_ENV", "dev"),
    val host: String = env("API_HOST", "0.0.0.0"),
    val port: Int = env("API_PORT", "8080").toIntOrNull() ?: 8080,
    val jwtSecret: String = env("JWT_SECRET", "local-development-secret"),
    val jwtExpiryMinutes: Int = env("JWT_EXPIRY_MINUTES", "60").toIntOrNull() ?: 60
) {
    init {
        require(port in 1..65535) { "API_PORT must be between 1 and 65535" }
        require(jwtExpiryMinutes > 0) { "JWT_EXPIRY_MINUTES must be greater than zero" }
        if (environment.equals("prod", ignoreCase = true) || environment.equals("production", ignoreCase = true)) {
            require(jwtSecret.length >= 32) { "JWT_SECRET must be at least 32 characters in production" }
            require(jwtSecret != "local-development-secret") { "JWT_SECRET must be changed in production" }
        }
    }
}

private fun env(name: String, default: String) = System.getenv(name) ?: default
