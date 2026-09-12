package persistence

import org.jetbrains.exposed.sql.Database

data class DatabaseConfig(
    val jdbcUrl: String = env("DATABASE_URL", "jdbc:postgresql://localhost:5432/chesscoach"),
    val user: String = env("DATABASE_USER", "chesscoach"),
    val password: String = env("DATABASE_PASSWORD", "chesscoach"),
    val maxPoolSize: Int = env("DATABASE_MAX_POOL_SIZE", "10").toIntOrNull() ?: 10,
) {
    init {
        require(maxPoolSize > 0) { "DATABASE_MAX_POOL_SIZE must be greater than zero" }
    }

    fun connect(): Database =
        Database.connect(
            jdbcUrl,
            driver = "org.postgresql.Driver",
            user = user,
            password = password,
        )
}

private fun env(
    name: String,
    default: String,
) = System.getenv(name) ?: default
