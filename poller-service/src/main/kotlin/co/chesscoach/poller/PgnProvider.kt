package co.chesscoach.poller

data class ProviderGame(
    val id: String,
    val pgn: String,
    val playedAsWhite: Boolean,
    val endTime: String,
)

interface PgnProviderClient {
    suspend fun recentGames(
        username: String,
        since: String? = null,
    ): List<ProviderGame>
}

class ConfigurablePgnProviderClient : PgnProviderClient {
    private val enabled = (System.getenv("PGN_PROVIDER_ENABLED") ?: "false").toBoolean()

    override suspend fun recentGames(
        username: String,
        since: String?,
    ) = if (enabled) emptyList<ProviderGame>() else emptyList() // Safe local stub: never calls external services.
}

class GamePoller(
    private val provider: PgnProviderClient,
    private val username: String,
) {
    suspend fun poll(since: String? = null) = provider.recentGames(username, since)
}
