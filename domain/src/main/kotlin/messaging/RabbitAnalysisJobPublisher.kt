package messaging

import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.MessageProperties
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RabbitAnalysisJobPublisher(
    private val host: String = System.getenv("RABBITMQ_HOST") ?: "localhost",
    private val queue: String = System.getenv("RABBITMQ_QUEUE") ?: "analysis.jobs",
    private val port: Int = System.getenv("RABBITMQ_PORT")?.toIntOrNull() ?: 5672,
    private val user: String = System.getenv("RABBITMQ_USER") ?: "chesscoach",
    private val password: String = System.getenv("RABBITMQ_PASSWORD") ?: "rabbitmq-dev-password",
) : AnalysisJobPublisher,
    AutoCloseable {
    init {
        println("[rabbitmq] connecting host=$host port=$port user=$user queue=$queue")
    }

    private val connection =
        ConnectionFactory()
            .apply {
                this.host = host
                this.port = port
                this.username = user
                this.password = password
                this.connectionTimeout = 10_000
            }.newConnection()
    private val channel = connection.createChannel().apply {
        queueDeclare(queue, true, false, false, null)
    }.also {
        println("[rabbitmq] connected host=$host port=$port queue=$queue")
    }

    override suspend fun publishFastAnalysis(gameId: String) =
        publish(gameId, INITIAL_POSITION_FEN, AnalysisTier.FAST)

    override suspend fun publishDeepAnalysis(gameId: String) =
        publish(gameId, INITIAL_POSITION_FEN, AnalysisTier.DEEP)

    private fun publish(
        gameId: String,
        fen: String,
        tier: AnalysisTier,
    ) {
        val body = Json.encodeToString(AnalysisJob(gameId, fen, tier)).toByteArray()
        channel.basicPublish("", queue, MessageProperties.PERSISTENT_TEXT_PLAIN, body)
        println("[api] analysis.queued gameId=$gameId tier=$tier queue=$queue")
    }

    override fun close() {
        channel.close()
        connection.close()
    }
}

private const val INITIAL_POSITION_FEN =
    "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
