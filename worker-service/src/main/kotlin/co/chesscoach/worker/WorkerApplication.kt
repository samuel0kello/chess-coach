package co.chesscoach.worker

import co.chesscoach.worker.engine.ChessEngine
import co.chesscoach.worker.engine.StockfishConfig
import co.chesscoach.worker.engine.StockfishEngine
import messaging.AnalysisJobPublisher
import messaging.RabbitAnalysisJobPublisher
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import persistence.*
import schema.*

private val workerModule =
    module {
        single<AnalysisJobPublisher> { RabbitAnalysisJobPublisher() }
        single { StockfishConfig() }
        single<ChessEngine> { StockfishEngine(get()) }
        single { AnalysisWorker(get(), get()) }
        single { DatabaseConfig().connect() }
        single<domain.AnalysisRepository> { ExposedAnalysisRepository() }
        single { StockfishAnalysisService(get(), get()) }
    }

fun main() {
    val koin =
        startKoin {
            modules(workerModule)
        }

    val database = koin.koin.get<org.jetbrains.exposed.sql.Database>()
    transaction(database) {
        SchemaUtils.create(Users, ChessAccounts, Games, MoveEvaluations, Puzzles)
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
            (koin.koin.get<AnalysisJobPublisher>() as? AutoCloseable)?.close()
            koin.close()
            stopKoin()
        },
    )
    println("worker-service started")
}
