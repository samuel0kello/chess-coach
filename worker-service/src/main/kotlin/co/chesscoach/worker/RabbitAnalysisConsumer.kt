package co.chesscoach.worker

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import kotlinx.serialization.json.Json
import messaging.AnalysisJob

class RabbitAnalysisConsumer(
    private val worker: StockfishAnalysisService,
    private val host: String = System.getenv("RABBITMQ_HOST") ?: "localhost",
    private val queue: String = System.getenv("RABBITMQ_QUEUE") ?: "analysis.jobs",
    private val port: Int = System.getenv("RABBITMQ_PORT")?.toIntOrNull() ?: 5672,
    private val user: String = System.getenv("RABBITMQ_USER") ?: "guest",
    private val password: String = System.getenv("RABBITMQ_PASSWORD") ?: "guest",
) : AutoCloseable {
    private val connection = connectWithRetry()
    private val channel = connection.createChannel()

    fun start() {
        channel.queueDeclare(queue, true, false, false, null)
        channel.basicQos(1)
        channel.basicConsume(
            queue,
            false,
            object : DefaultConsumer(channel) {
                override fun handleDelivery(
                    tag: String,
                    envelope: Envelope,
                    props: AMQP.BasicProperties,
                    body: ByteArray,
                ) {
                    println("[worker] analysis.start deliveryTag=${envelope.deliveryTag} payload=${body.toString(Charsets.UTF_8)}")
                    try {
                        val job = Json.decodeFromString<AnalysisJob>(body.toString(Charsets.UTF_8))
                        val result = worker.analyzeAndPersist(job)
                        channel.basicAck(envelope.deliveryTag, false)
                        println("[worker] analysis.complete gameId=${job.gameId} tier=${job.tier} depth=${result.depth}")
                    } catch (error: Exception) {
                        println("[worker] analysis.failed deliveryTag=${envelope.deliveryTag} error=${error.message}")
                        error.printStackTrace()
                        channel.basicNack(envelope.deliveryTag, false, true)
                    }
                }
            },
        )
    }

    override fun close() {
        channel.close()
        connection.close()
    }

    private fun connectWithRetry(): Connection {
        // Give RabbitMQ extra time to be fully ready after health check passes
        println("[worker] waiting 5 seconds for RabbitMQ to be fully ready...")
        Thread.sleep(5_000)

        // Use AMQP URI format for simpler connection configuration
        val uri = "amqp://$user:$password@$host:$port/%2F"
        println("[worker] connecting via AMQP URI: amqp://$user:****@$host:$port/%2F")

        val factory = ConnectionFactory()
        factory.setUri(uri)
        factory.connectionTimeout = 30_000
        factory.requestedHeartbeat = 30
        factory.isAutomaticRecoveryEnabled = true
        factory.networkRecoveryInterval = 10_000

        var lastError: Exception? = null
        repeat(15) { attempt ->
            try {
                println(
                    "[worker] connecting rabbitmq host=$host port=$port user=$user attempt=${attempt + 1}/15",
                )
                return factory.newConnection()
            } catch (error: Exception) {
                lastError = error
                println("[worker] rabbitmq connection failed type=${error::class.simpleName} message=${error.message}")
                error.printStackTrace()
                if (attempt < 14) Thread.sleep(2_000)
            }
        }
        throw IllegalStateException("Could not connect to RabbitMQ at $host:$port after 15 attempts", lastError)
    }
}
