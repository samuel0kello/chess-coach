package persistence

import domain.AnalysisRepository
import domain.AnalysisResult
import domain.ChessAccount
import domain.ChessAccountRepository
import domain.Game
import domain.GameRepository
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
import schema.ChessAccounts
import schema.Games
import schema.MoveEvaluations
import schema.Puzzles
import schema.Users
import java.time.Instant

class ExposedGameRepository : GameRepository {
    override fun find(id: String) =
        transaction {
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

    override fun save(result: AnalysisResult) {
        transaction {
            MoveEvaluations.insert {
                it[gameId] = result.gameId
                it[ply] = 0
                it[sanMove] = result.bestMove.take(16)
                it[centipawnLoss] = result.score ?: 0
                it[quality] = "engine"
                it[phase] = "analysis"
                it[analysisTier] = if (result.depth > 15) "DEEP" else "FAST"
            }
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
