package co.chesscoach.worker.engine

interface ChessEngine : AutoCloseable {
    fun analyze(
        fen: String,
        depth: Int,
    ): EngineAnalysis
}

data class EngineAnalysis(
    val bestMove: String,
    val score: EngineScore?,
    val principalVariation: List<String>,
)

sealed interface EngineScore {
    data class Centipawns(
        val value: Int,
    ) : EngineScore

    data class Mate(
        val moves: Int,
    ) : EngineScore
}
