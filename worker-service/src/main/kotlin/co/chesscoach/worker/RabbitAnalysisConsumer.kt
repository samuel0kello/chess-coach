package co.chesscoach.worker

import com.rabbitmq.client.*
import java.net.InetAddress
import kotlinx.serialization.json.Json
import messaging.AnalysisJob

class RabbitAnalysisConsumer(
    private val worker: StockfishAnalysisService,
    private val host: String = System.getenv("RABBITMQ_HOST") ?: "localhost",
    private val queue: String = System.getenv("RABBITMQ_QUEUE") ?: "analysis.jobs",
    private val port: Int = System.getenv("RABBITMQ_PORT")?.toIntOrNull() ?: 5672,
    private val user: String = System.getenv("RABBITMQ_USER") ?: "guest",
    private val password: String = System.getenv("RABBITMQ_PASSWORD") ?: "guest"
) : AutoCloseable {
    private val connection = connectWithRetry()
    private val channel = connection.createChannel()
    fun start() {
        channel.queueDeclare(queue, true, false, false, null)
        channel.basicQos(1)
        channel.basicConsume(queue, false, object : DefaultConsumer(channel) {
            override fun handleDelivery(tag: String, envelope: Envelope, props: AMQP.BasicProperties, body: ByteArray) {
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
        })
    }
    override fun close() { channel.close(); connection.close() }

    private fun connectWithRetry(): Connection {
        val factory = ConnectionFactory().apply {
            this.host = host
            this.port = port
            this.username = user
            this.password = password
            this.virtualHost = "/"
            connectionTimeout = 10_000
        }
        var lastError: Exception? = null
        repeat(15) { attempt ->
            try {
                println(
                    "[worker] connecting rabbitmq host=$host resolved=${InetAddress.getAllByName(host).joinToString()} " +
                        "port=$port user=$user attempt=${attempt + 1}/15"
                )
                return factory.newConnection()
            } catch (error: Exception) {
                lastError = error
                println("[worker] rabbitmq connection failed type=${error::class.simpleName} message=${error.message}")
                if (attempt < 14) Thread.sleep(2_000)
            }
        }
        throw IllegalStateException("Could not connect to RabbitMQ at $host:$port after 15 attempts", lastError)
    }
}
