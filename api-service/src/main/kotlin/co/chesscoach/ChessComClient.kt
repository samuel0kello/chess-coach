package co.chesscoach

import domain.Game
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant

class ChessComClient(
    private val httpClient: HttpClient = HttpClient.newBuilder().build(),
) {
    fun recentGames(
        username: String,
        months: Int = 3,
    ): List<Game> {
        require(username.matches(Regex("[A-Za-z0-9_-]{3,64}"))) {
            "Invalid Chess.com username"
        }

        val allArchives: List<String> =
            getJsonObject("https://api.chess.com/pub/player/$username/games/archives")
                .getJsonArray("archives")
                ?.map { it.toString() }
                ?: error("Chess.com archives response did not contain an archives array")
        val archives = allArchives.takeLast(months)

        println("[chess.com] username=$username archives=${archives.size}")
        return archives.flatMap { archiveUrl ->
            val games =
                getJsonObject(archiveUrl).getJsonArray("games")
                    ?: error("Chess.com archive response did not contain a games array: $archiveUrl")
            println("[chess.com] username=$username archive=$archiveUrl games=${games.size()}")
            games.mapNotNull { value ->
                val game = value as? io.vertx.core.json.JsonObject
                if (game == null) {
                    println("[chess.com] skipped non-object game payload archive=$archiveUrl")
                    null
                } else {
                    parseGame(game, username)
                }
            }
        }
    }

    private fun parseGame(
        json: io.vertx.core.json.JsonObject,
        username: String,
    ): Game? {
        val id = json.getString("url") ?: return null
        val pgn = json.getString("pgn") ?: return null
        val white = json.getJsonObject("white") ?: return null
        val black = json.getJsonObject("black") ?: return null
        val whiteUsername = white.getString("username", "")
        val blackUsername = black.getString("username", "")
        if (!whiteUsername.equals(username, ignoreCase = true) &&
            !blackUsername.equals(username, ignoreCase = true)
        ) {
            return null
        }

        val endTime = json.getLong("end_time") ?: return null
        return Game(
            id = id,
            accountId = "",
            pgn = pgn,
            playedAsWhite = whiteUsername.equals(username, ignoreCase = true),
            timeClass = json.getString("time_class", "unknown"),
            openingEco = normalizeEco(json.getString("eco")),
            termination = json.getString("termination", "unknown"),
            endTime = Instant.ofEpochSecond(endTime).toString(),
        )
    }

    private fun getJsonObject(url: String): io.vertx.core.json.JsonObject =
        io.vertx.core.json
            .JsonObject(get(url))

    private fun get(url: String): String {
        val request =
            HttpRequest
                .newBuilder(URI.create(url))
                .header("User-Agent", "ChessCoachBackend/1.0 (game analysis service)")
                .header("Accept", "application/json")
                .GET()
                .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) {
            "Chess.com returned HTTP ${response.statusCode()} for $url: ${response.body().take(300)}"
        }
        return response.body()
    }
}

internal fun normalizeEco(eco: String?): String? =
    eco
        ?.substringAfterLast('/')
        ?.takeIf { it.isNotBlank() }
