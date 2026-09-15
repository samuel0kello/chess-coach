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
data class AnalysisJobStatus(
    val gameId: String,
    val status: String, // queued, running, completed, failed
    val tier: String,
    val progress: Int, // 0-100
    val totalMoves: Int,
    val movesAnalyzed: Int,
    val createdAt: String,
    val startedAt: String?,
    val completedAt: String?,
)

@Serializable
data class MoveAnalysis(
    val gameId: String,
    val ply: Int,
    val sanMove: String,
    val evaluationBefore: Int,
    val evaluationAfter: Int,
    val centipawnLoss: Int,
    val bestMove: String,
    val principalVariation: List<String>,
    val moveQuality: MoveQuality,
    val phase: GamePhase,
    val analysisTier: String,
    val timestamp: String = Instant.now().toString(),
)

@Serializable
enum class MoveQuality {
    BRILLIANT,
    GREAT,
    BEST,
    EXCELLENT,
    GOOD,
    BOOK,
    INACCURACY,
    MISTAKE,
    BLUNDER,
    UNKNOWN,
}

@Serializable
enum class GamePhase {
    OPENING,
    MIDDLEGAME,
    ENDGAME,
}

@Serializable
data class GameAnalysisSummary(
    val gameId: String,
    val totalMoves: Int,
    val accuracy: Double,
    val bestMoves: Int,
    val excellentMoves: Int,
    val goodMoves: Int,
    val bookMoves: Int,
    val inaccuracies: Int,
    val mistakes: Int,
    val blunders: Int,
    val avgCentipawnLoss: Double,
    val phaseAnalysis: Map<String, PhaseStats>,
    val analysisTier: String,
    val completedAt: String = Instant.now().toString(),
)

@Serializable
data class PhaseStats(
    val phase: GamePhase,
    val moves: Int,
    val accuracy: Double,
    val avgCentipawnLoss: Double,
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

    fun normalizeGameId(id: String): String
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

    fun findMoveAnalysis(gameId: String): List<MoveAnalysis>

    fun findGameSummary(gameId: String): GameAnalysisSummary?

    fun save(result: AnalysisResult)

    fun saveMoveAnalysis(analysis: MoveAnalysis)

    fun saveGameSummary(summary: GameAnalysisSummary)
}

interface AnalysisJobRepository {
    fun createJob(
        gameId: String,
        tier: String,
    ): String

    fun updateJobProgress(
        gameId: String,
        progress: Int,
        movesAnalyzed: Int,
        totalMoves: Int,
    )

    fun updateJobStatus(
        gameId: String,
        status: String,
    )

    fun getJobStatus(gameId: String): AnalysisJobStatus?
}

interface PuzzleRepository {
    fun list(limit: Int = 50): List<Puzzle>
}

interface UserRepository {
    fun findByEmail(email: String): UserAccount?

    fun save(user: UserAccount)
}
