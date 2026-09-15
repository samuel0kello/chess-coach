package co.chesscoach.worker

import co.chesscoach.worker.chess.MoveQualityClassifier
import co.chesscoach.worker.chess.PgnParser
import co.chesscoach.worker.engine.ChessEngine
import co.chesscoach.worker.engine.StockfishConfig
import co.chesscoach.worker.engine.StockfishEngine
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import persistence.DatabaseConfig
import persistence.ExposedAnalysisJobRepository
import persistence.ExposedAnalysisRepository
import persistence.ExposedGameRepository
import schema.AnalysisJobs
import schema.ChessAccounts
import schema.Games
import schema.MoveEvaluations
import schema.Puzzles
import schema.Users

private val workerModule =
    module {
        single { StockfishConfig() }
        single<ChessEngine> { StockfishEngine(get()) }
        single { AnalysisWorker(get(), get()) }
        single { DatabaseConfig().connect() }
        single<domain.AnalysisRepository> { ExposedAnalysisRepository() }
        single<domain.GameRepository> { ExposedGameRepository() }
        single<domain.AnalysisJobRepository> { ExposedAnalysisJobRepository() }
        single { PgnParser() }
        single { MoveQualityClassifier() }
        single { MoveByMoveAnalyzer(get(), get(), get(), get()) }
        single { StockfishAnalysisService(get(), get(), get()) }
    }

fun main() {
    val koin =
        startKoin {
            modules(workerModule)
        }

    val database = koin.koin.get<org.jetbrains.exposed.sql.Database>()
    transaction(database) {
        SchemaUtils.create(Users, ChessAccounts, Games, MoveEvaluations, Puzzles, AnalysisJobs)
        exec("ALTER TABLE games ALTER COLUMN opening_eco TYPE varchar(255)")
    }
    val consumer = RabbitAnalysisConsumer(koin.koin.get())
    consumer.start()
    println(
        "[worker] started queue=${System.getenv("RABBITMQ_QUEUE") ?: "analysis.jobs"}" +
            " stockfish=${System.getenv("STOCKFISH_PATH") ?: "stockfish"}",
    )
    Runtime.getRuntime().addShutdownHook(
        Thread {
            consumer.close()
            koin.koin.get<ChessEngine>().close()
            koin.close()
            stopKoin()
        },
    )
    println("worker-service started")
}
