package persistence

import domain.AnalysisJobRepository
import domain.AnalysisJobStatus
import domain.AnalysisRepository
import domain.AnalysisResult
import domain.ChessAccount
import domain.ChessAccountRepository
import domain.Game
import domain.GameAnalysisSummary
import domain.GamePhase
import domain.GameRepository
import domain.MoveAnalysis
import domain.MoveQuality
import domain.Puzzle
import domain.PuzzleRepository
import domain.UserAccount
import domain.UserRepository
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import schema.AnalysisJobs
import schema.ChessAccounts
import schema.GameAnalysisSummaries
import schema.Games
import schema.MoveEvaluations
import schema.Puzzles
import schema.Users
import java.time.Instant

class ExposedGameRepository : GameRepository {
    override fun find(id: String) =
        transaction {
            // Try exact match first
            Games
                .selectAll()
                .where { Games.id eq id }
                .singleOrNull()
                ?.toGame()
                ?: Games
                    .selectAll()
                    .where { Games.id like "%/$id" }
                    .limit(1)
                    .singleOrNull()
                    ?.toGame()
        }

    override fun normalizeGameId(id: String): String {
        // If the ID looks like just a number, try to find the full URL in the database
        if (!id.startsWith("http")) {
            return transaction {
                Games
                    .selectAll()
                    .where { Games.id like "%/$id" }
                    .limit(1)
                    .singleOrNull()
                    ?.get(Games.id)
                    ?.value ?: id
            }
        }
        return id
    }

    override fun list(
        accountId: String,
        limit: Int,
    ) = transaction {
        Games
            .selectAll()
            .where { Games.chessAccountId eq java.util.UUID.fromString(accountId) }
            .limit(limit)
            .map { it.toGame() }
    }

    override fun save(game: Game) {
        transaction {
            Games.insertIgnore {
                it[id] = game.id
                it[chessAccountId] = java.util.UUID.fromString(game.accountId)
                it[pgn] = game.pgn
                it[playedAsWhite] = game.playedAsWhite
                it[timeClass] = game.timeClass
                it[openingEco] = game.openingEco
                it[termination] = game.termination
                it[endTime] = Instant.parse(game.endTime)
            }
        }
    }
}

class ExposedChessAccountRepository : ChessAccountRepository {
    override fun findByUserId(userId: String) =
        transaction {
            ChessAccounts
                .selectAll()
                .where { ChessAccounts.userId eq java.util.UUID.fromString(userId) }
                .singleOrNull()
                ?.let {
                    ChessAccount(
                        id = it[ChessAccounts.id].value.toString(),
                        userId = it[ChessAccounts.userId].value.toString(),
                        username = it[ChessAccounts.chessComUserName],
                        verified = it[ChessAccounts.verified],
                    )
                }
        }

    override fun save(account: ChessAccount) {
        transaction {
            ChessAccounts.insertIgnore {
                it[id] = java.util.UUID.fromString(account.id)
                it[userId] = java.util.UUID.fromString(account.userId)
                it[chessComUserName] = account.username
                it[verified] = account.verified
            }
        }
    }
}

class ExposedAnalysisRepository : AnalysisRepository {
    override fun findByGame(gameId: String) =
        transaction {
            MoveEvaluations.selectAll().where { MoveEvaluations.gameId eq gameId }.map {
                AnalysisResult(gameId, "", it[MoveEvaluations.sanMove], null, null, emptyList(), 0)
            }
        }

    override fun findMoveAnalysis(gameId: String): List<MoveAnalysis> =
        transaction {
            MoveEvaluations
                .selectAll()
                .where { MoveEvaluations.gameId eq gameId }
                .map {
                    MoveAnalysis(
                        gameId = gameId,
                        ply = it[MoveEvaluations.ply],
                        sanMove = it[MoveEvaluations.sanMove],
                        evaluationBefore = it[MoveEvaluations.evaluationBefore] ?: 0,
                        evaluationAfter = it[MoveEvaluations.evaluationAfter] ?: 0,
                        centipawnLoss = it[MoveEvaluations.centipawnLoss],
                        bestMove = it[MoveEvaluations.bestMove] ?: "",
                        principalVariation = it[MoveEvaluations.principalVariation]?.split(",") ?: emptyList(),
                        moveQuality = parseMoveQuality(it[MoveEvaluations.moveQuality]),
                        phase = parseGamePhase(it[MoveEvaluations.phase]),
                        analysisTier = it[MoveEvaluations.analysisTier],
                        timestamp = Instant.now().toString(),
                    )
                }.sortedBy { it.ply }
        }

    override fun findGameSummary(gameId: String): GameAnalysisSummary? =
        transaction {
            GameAnalysisSummaries
                .selectAll()
                .where { GameAnalysisSummaries.gameId eq gameId }
                .singleOrNull()
                ?.let {
                    GameAnalysisSummary(
                        gameId = gameId,
                        totalMoves = it[GameAnalysisSummaries.totalMoves],
                        accuracy = it[GameAnalysisSummaries.accuracy],
                        bestMoves = it[GameAnalysisSummaries.bestMoves],
                        excellentMoves = it[GameAnalysisSummaries.excellentMoves],
                        goodMoves = it[GameAnalysisSummaries.goodMoves],
                        bookMoves = it[GameAnalysisSummaries.bookMoves],
                        inaccuracies = it[GameAnalysisSummaries.inaccuracies],
                        mistakes = it[GameAnalysisSummaries.mistakes],
                        blunders = it[GameAnalysisSummaries.blunders],
                        avgCentipawnLoss = it[GameAnalysisSummaries.avgCentipawnLoss],
                        phaseAnalysis = emptyMap(), // Simplified for now
                        analysisTier = it[GameAnalysisSummaries.analysisTier],
                        completedAt = it[GameAnalysisSummaries.completedAt].toString(),
                    )
                }
        }

    override fun save(result: AnalysisResult) {
        transaction {
            MoveEvaluations.insert {
                it[gameId] = result.gameId
                it[ply] = 0
                it[sanMove] = result.bestMove.take(32) // Increased from 16 to 32
                it[centipawnLoss] = result.score ?: 0
                it[quality] = "engine"
                it[phase] = "analysis"
                it[analysisTier] = if (result.depth > 15) "DEEP" else "FAST"
            }
        }
    }

    override fun saveMoveAnalysis(analysis: MoveAnalysis) {
        transaction {
            MoveEvaluations.insert {
                it[gameId] = analysis.gameId
                it[ply] = analysis.ply
                it[sanMove] = analysis.sanMove
                it[evaluationBefore] = analysis.evaluationBefore
                it[evaluationAfter] = analysis.evaluationAfter
                it[centipawnLoss] = analysis.centipawnLoss
                it[bestMove] = analysis.bestMove
                it[principalVariation] = analysis.principalVariation.joinToString(",")
                it[moveQuality] = analysis.moveQuality.name
                it[quality] = analysis.moveQuality.name
                it[phase] = analysis.phase.name
                it[analysisTier] = analysis.analysisTier
            }
        }
    }

    override fun saveGameSummary(summary: GameAnalysisSummary) {
        transaction {
            GameAnalysisSummaries.insert {
                it[gameId] = summary.gameId
                it[totalMoves] = summary.totalMoves
                it[accuracy] = summary.accuracy
                it[bestMoves] = summary.bestMoves
                it[excellentMoves] = summary.excellentMoves
                it[goodMoves] = summary.goodMoves
                it[bookMoves] = summary.bookMoves
                it[inaccuracies] = summary.inaccuracies
                it[mistakes] = summary.mistakes
                it[blunders] = summary.blunders
                it[avgCentipawnLoss] = summary.avgCentipawnLoss
                it[analysisTier] = summary.analysisTier
                it[completedAt] = Instant.parse(summary.completedAt)
            }
        }
    }

    private fun parseMoveQuality(quality: String?): MoveQuality =
        try {
            MoveQuality.valueOf(quality ?: "UNKNOWN")
        } catch (e: IllegalArgumentException) {
            MoveQuality.UNKNOWN
        }

    private fun parseGamePhase(phase: String?): GamePhase =
        try {
            GamePhase.valueOf(phase ?: "MIDDLEGAME")
        } catch (e: IllegalArgumentException) {
            GamePhase.MIDDLEGAME
        }
}

class ExposedAnalysisJobRepository : AnalysisJobRepository {
    override fun createJob(
        gameId: String,
        tier: String,
    ): String =
        transaction {
            AnalysisJobs.insert {
                it[AnalysisJobs.gameId] = gameId
                it[status] = "queued"
                it[AnalysisJobs.tier] = tier
                it[AnalysisJobs.progress] = 0
                it[AnalysisJobs.totalMoves] = 0
                it[AnalysisJobs.movesAnalyzed] = 0
                it[AnalysisJobs.createdAt] = Instant.now()
            }
            AnalysisJobs
                .selectAll()
                .where { AnalysisJobs.gameId eq gameId }
                .singleOrNull()
                ?.get(AnalysisJobs.id)
                ?.value
                ?.toString() ?: ""
        }

    override fun updateJobProgress(
        gameId: String,
        progress: Int,
        movesAnalyzed: Int,
        totalMoves: Int,
    ) {
        transaction {
            AnalysisJobs.update({ AnalysisJobs.gameId eq gameId }) {
                it[AnalysisJobs.progress] = progress
                it[AnalysisJobs.movesAnalyzed] = movesAnalyzed
                it[AnalysisJobs.totalMoves] = totalMoves
            }
        }
    }

    override fun updateJobStatus(
        gameId: String,
        status: String,
    ) {
        transaction {
            when (status) {
                "running" -> {
                    AnalysisJobs.update({ AnalysisJobs.gameId eq gameId }) {
                        it[AnalysisJobs.status] = status
                        it[AnalysisJobs.startedAt] = Instant.now()
                    }
                }

                "completed" -> {
                    AnalysisJobs.update({ AnalysisJobs.gameId eq gameId }) {
                        it[AnalysisJobs.status] = status
                        it[AnalysisJobs.completedAt] = Instant.now()
                        it[AnalysisJobs.progress] = 100
                    }
                }

                "failed" -> {
                    AnalysisJobs.update({ AnalysisJobs.gameId eq gameId }) {
                        it[AnalysisJobs.status] = status
                        it[AnalysisJobs.completedAt] = Instant.now()
                    }
                }

                else -> {
                    AnalysisJobs.update({ AnalysisJobs.gameId eq gameId }) {
                        it[AnalysisJobs.status] = status
                    }
                }
            }
        }
    }

    override fun getJobStatus(gameId: String): AnalysisJobStatus? =
        transaction {
            AnalysisJobs
                .selectAll()
                .where { AnalysisJobs.gameId eq gameId }
                .singleOrNull()
                ?.let {
                    AnalysisJobStatus(
                        gameId = gameId,
                        status = it[AnalysisJobs.status],
                        tier = it[AnalysisJobs.tier],
                        progress = it[AnalysisJobs.progress],
                        totalMoves = it[AnalysisJobs.totalMoves],
                        movesAnalyzed = it[AnalysisJobs.movesAnalyzed],
                        createdAt = it[AnalysisJobs.createdAt].toString(),
                        startedAt = it[AnalysisJobs.startedAt]?.toString(),
                        completedAt = it[AnalysisJobs.completedAt]?.toString(),
                    )
                }
        }
}

class ExposedPuzzleRepository : PuzzleRepository {
    override fun list(limit: Int) =
        transaction {
            Puzzles.selectAll().limit(limit).map {
                Puzzle(
                    it[Puzzles.id].value.toString(),
                    it[Puzzles.gameId].value,
                    it[Puzzles.fen],
                    it[Puzzles.theme],
                    it[Puzzles.difficulty],
                )
            }
        }
}

class ExposedUserRepository : UserRepository {
    override fun findByEmail(email: String) =
        transaction {
            Users.selectAll().where { Users.email eq email }.singleOrNull()?.let {
                UserAccount(it[Users.id].value.toString(), it[Users.email], it[Users.passwordHash])
            }
        }

    override fun save(user: UserAccount) {
        transaction {
            Users.insert {
                it[Users.id] = java.util.UUID.fromString(user.id)
                it[Users.email] = user.email
                it[Users.passwordHash] =
                    user.passwordHash
            }
        }
    }
}

private fun ResultRow.toGame() =
    Game(
        this[Games.id].value,
        this[Games.chessAccountId].value.toString(),
        this[Games.pgn],
        this[Games.playedAsWhite],
        this[Games.timeClass],
        this[Games.openingEco],
        this[Games.termination],
        this[Games.endTime].toString(),
    )
