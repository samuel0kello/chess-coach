package co.chesscoach.worker

import co.chesscoach.worker.engine.ChessEngine
import co.chesscoach.worker.engine.EngineAnalysis
import co.chesscoach.worker.engine.StockfishConfig
import messaging.AnalysisJob
import messaging.AnalysisTier

class AnalysisWorker(
    private val engine: ChessEngine,
    private val config: StockfishConfig
) {
    fun analyze(job: AnalysisJob): EngineAnalysis {
        val depth = when (job.tier) {
            AnalysisTier.FAST -> config.fastDepth
            AnalysisTier.DEEP -> config.deepDepth
        }
        return engine.analyze(job.fen, depth)
    }
}
