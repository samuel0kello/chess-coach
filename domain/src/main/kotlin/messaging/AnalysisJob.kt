package messaging

import kotlinx.serialization.Serializable

@Serializable
data class AnalysisJob(
    val gameId: String,
    val fen: String,
    val tier: AnalysisTier
)

@Serializable
enum class AnalysisTier {
    FAST,
    DEEP
}
