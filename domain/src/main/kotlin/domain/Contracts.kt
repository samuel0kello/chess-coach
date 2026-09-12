package domain

import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class Game(
    val id: String,
    val accountId: String,
    val pgn: String,
    val playedAsWhite: Boolean,
    val timeClass: String = "unknown",
    val openingEco: String? = null,
    val termination: String = "unknown",
    val endTime: String,
)

@Serializable
data class AnalysisResult(
    val gameId: String,
    val fen: String,
    val bestMove: String,
    val score: Int?,
    val mate: Int?,
    val principalVariation: List<String>,
    val depth: Int,
    val createdAt: String = Instant.now().toString(),
)

@Serializable
data class Puzzle(
    val id: String,
    val gameId: String,
    val fen: String,
    val theme: String,
    val difficulty: Int,
)

data class UserAccount(
    val id: String,
    val email: String,
    val passwordHash: String,
)

interface GameRepository {
    fun find(id: String): Game?

    fun list(
        accountId: String,
        limit: Int = 50,
    ): List<Game>

    fun save(game: Game)
}

data class ChessAccount(
    val id: String,
    val userId: String,
    val username: String,
    val verified: Boolean = true,
)

interface ChessAccountRepository {
    fun findByUserId(userId: String): ChessAccount?

    fun save(account: ChessAccount)
}

interface AnalysisRepository {
    fun findByGame(gameId: String): List<AnalysisResult>

    fun save(result: AnalysisResult)
}

interface PuzzleRepository {
    fun list(limit: Int = 50): List<Puzzle>
}

interface UserRepository {
    fun findByEmail(email: String): UserAccount?

    fun save(user: UserAccount)
}
