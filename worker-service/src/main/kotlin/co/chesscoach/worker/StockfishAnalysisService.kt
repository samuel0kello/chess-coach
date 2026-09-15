package co.chesscoach.worker

import co.chesscoach.worker.engine.EngineScore
import domain.AnalysisRepository
import domain.AnalysisResult
import domain.GameRepository
import messaging.AnalysisJob
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class StockfishAnalysisService(
    private val worker: AnalysisWorker,
    private val repository: AnalysisRepository,
    private val moveByMoveAnalyzer: MoveByMoveAnalyzer,
) : KoinComponent {
    private val gameRepository: GameRepository by inject()

    fun analyzeAndPersist(job: AnalysisJob): AnalysisResult {
        // Normalize game ID (convert numeric ID to full URL if needed)
        val normalizedGameId = gameRepository.normalizeGameId(job.gameId)
        val normalizedJob = job.copy(gameId = normalizedGameId)

        // Check if this is a full game analysis (move-by-move) or single position
        val game = gameRepository.find(normalizedGameId)

        if (game != null && normalizedJob.fen == "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1") {
            // This is a full game analysis request - use move-by-move analyzer
            return analyzeFullGame(normalizedJob, game.pgn)
        } else {
            // Single position analysis
            return analyzeSinglePosition(normalizedJob)
        }
    }

    private fun analyzeFullGame(
        job: AnalysisJob,
        pgn: String,
    ): AnalysisResult {
        try {
            val summary = moveByMoveAnalyzer.analyzeGame(job.gameId, pgn, job.tier)

            // Return a simplified result for compatibility
            return AnalysisResult(
                gameId = job.gameId,
                fen = job.fen,
                bestMove = "move-by-move",
                score = summary.accuracy.toInt(),
                mate = null,
                principalVariation = emptyList(),
                depth = if (job.tier == messaging.AnalysisTier.DEEP) 20 else 12,
            )
        } catch (e: Exception) {
            println("[worker] Full game analysis failed: ${e.message}")
            // Fall back to single position analysis
            return analyzeSinglePosition(job)
        }
    }

    private fun analyzeSinglePosition(job: AnalysisJob): AnalysisResult {
        val analysis = worker.analyze(job)
        val result =
            AnalysisResult(
                gameId = job.gameId,
                fen = job.fen,
                bestMove = analysis.bestMove,
                score = (analysis.score as? EngineScore.Centipawns)?.value,
                mate = (analysis.score as? EngineScore.Mate)?.moves,
                principalVariation = analysis.principalVariation,
                depth = if (job.tier == messaging.AnalysisTier.DEEP) 20 else 12,
            )
        repository.save(result)
        return result
    }
}
