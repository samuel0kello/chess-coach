package co.chesscoach.worker

import co.chesscoach.worker.engine.EngineScore
import domain.AnalysisRepository
import domain.AnalysisResult
import messaging.AnalysisJob

class StockfishAnalysisService(
    private val worker: AnalysisWorker,
    private val repository: AnalysisRepository
) {
    fun analyzeAndPersist(job: AnalysisJob): AnalysisResult {
        val analysis = worker.analyze(job)
        val result = AnalysisResult(
            gameId = job.gameId,
            fen = job.fen,
            bestMove = analysis.bestMove,
            score = (analysis.score as? EngineScore.Centipawns)?.value,
            mate = (analysis.score as? EngineScore.Mate)?.moves,
            principalVariation = analysis.principalVariation,
            depth = if (job.tier == messaging.AnalysisTier.DEEP) 20 else 12
        )
        repository.save(result)
        return result
    }
}
