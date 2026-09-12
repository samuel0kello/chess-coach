package messaging

interface AnalysisJobPublisher {
    suspend fun publishFastAnalysis(gameId: String)

    suspend fun publishDeepAnalysis(gameId: String)
}

// Stand-in until stage 4(rabitmq) is designed. Logs instead of queueing —
// swap the Koin binding for a RabbitMQ-backed implementation later,
// nothing that calls this interface has to change.
class LoggingAnalysisJobPublisher : AnalysisJobPublisher {
    override suspend fun publishFastAnalysis(gameId: String) {
        println("[stub] would publish fast-analysis job for $gameId")
    }

    override suspend fun publishDeepAnalysis(gameId: String) {
        println("[stub] would publish deep-analysis job for $gameId")
    }
}
