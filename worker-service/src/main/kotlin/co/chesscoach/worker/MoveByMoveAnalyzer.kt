package co.chesscoach.worker

import co.chesscoach.worker.chess.MoveQualityClassifier
import co.chesscoach.worker.chess.PgnParseResult
import co.chesscoach.worker.chess.PgnParser
import co.chesscoach.worker.engine.ChessEngine
import co.chesscoach.worker.engine.EngineScore
import domain.AnalysisJobRepository
import domain.AnalysisRepository
import domain.GameAnalysisSummary
import domain.GamePhase
import domain.MoveAnalysis
import domain.PhaseStats
import messaging.AnalysisTier
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Instant

/**
 * Move-by-Move Analyzer using Stockfish for FEN analysis
 * Analyzes each position in a game with Chess.com-like quality classification
 */
class MoveByMoveAnalyzer(
    private val engine: ChessEngine,
    private val qualityClassifier: MoveQualityClassifier,
    private val repository: AnalysisRepository,
    private val jobRepository: AnalysisJobRepository,
    private val fastDepth: Int = 12,
    private val deepDepth: Int = 20,
) : KoinComponent {
    private val gameRepository: domain.GameRepository by inject()

    /**
     * Analyzes a complete game move-by-move
     */
    fun analyzeGame(
        gameId: String,
        pgn: String,
        tier: AnalysisTier,
    ): GameAnalysisSummary {
        val depth = if (tier == AnalysisTier.DEEP) deepDepth else fastDepth
        val moveAnalyses = mutableListOf<MoveAnalysis>()

        // Update job status to running
        jobRepository.updateJobStatus(gameId, "running")

        // Parse PGN using simple parser
        val parser = PgnParser()
        val parseResult = parser.parsePgn(pgn)

        if (parseResult is PgnParseResult.Error) {
            jobRepository.updateJobStatus(gameId, "failed")
            throw IllegalArgumentException("Failed to parse PGN: ${parseResult.message}")
        }

        val gameData = parseResult as PgnParseResult.Success
        val moves = gameData.moves

        if (moves.isEmpty()) {
            jobRepository.updateJobStatus(gameId, "failed")
            throw IllegalArgumentException("No moves found in PGN")
        }

        // Update total moves in job
        jobRepository.updateJobProgress(gameId, 0, 0, moves.size)

        // Analyze each move
        moves.forEachIndexed { index, parsedMove ->
            val startTime = System.currentTimeMillis()

            // Analyze position before the move
            val analysisBefore = engine.analyze(parsedMove.fenBefore, depth)
            val evaluationBefore = getEvaluation(analysisBefore.score)

            // Analyze position after the move
            val analysisAfter = engine.analyze(parsedMove.fenAfter, depth)
            val evaluationAfter = getEvaluation(analysisAfter.score)

            val centipawnLoss = evaluationBefore - evaluationAfter
            val phase = determinePhase(index, moves.size)
            val complexity = qualityClassifier.calculateComplexity(evaluationBefore, phase, parsedMove.moveNumber)

            val moveQuality =
                qualityClassifier.classifyMove(
                    evaluationBefore = evaluationBefore,
                    evaluationAfter = evaluationAfter,
                    bestMoveEvaluation = evaluationAfter,
                    actualMove = parsedMove.san,
                    bestMove = analysisAfter.bestMove,
                    phase = phase,
                    complexity = complexity,
                )

            moveAnalyses.add(
                MoveAnalysis(
                    gameId = gameId,
                    ply = index + 1,
                    sanMove = parsedMove.san,
                    evaluationBefore = evaluationBefore,
                    evaluationAfter = evaluationAfter,
                    centipawnLoss = centipawnLoss,
                    bestMove = analysisAfter.bestMove,
                    principalVariation = analysisAfter.principalVariation,
                    moveQuality = moveQuality,
                    phase = phase,
                    analysisTier = tier.name,
                    timestamp = Instant.now().toString(),
                ),
            )

            // Save move analysis to database
            repository.saveMoveAnalysis(moveAnalyses.last())

            // Update progress
            val progress = ((index + 1).toDouble() / moves.size * 100).toInt()
            jobRepository.updateJobProgress(gameId, progress, index + 1, moves.size)
        }

        // Calculate and save game summary
        val summary = calculateSummary(gameId, moveAnalyses, tier)
        repository.saveGameSummary(summary)

        // Update job status to completed
        jobRepository.updateJobStatus(gameId, "completed")

        return summary
    }

    /**
     * Get evaluation from engine score
     */
    private fun getEvaluation(score: EngineScore?): Int =
        when (score) {
            is EngineScore.Centipawns -> score.value

            is EngineScore.Mate -> 10000

            // Convert mate to high centipawn value
            null -> 0
        }

    /**
     * Determine game phase based on move index
     */
    private fun determinePhase(
        moveIndex: Int,
        totalMoves: Int,
    ): GamePhase {
        val moveNumber = (moveIndex / 2) + 1
        return when {
            moveNumber < 10 -> GamePhase.OPENING
            moveNumber > totalMoves - 10 -> GamePhase.ENDGAME
            else -> GamePhase.MIDDLEGAME
        }
    }

    /**
     * Calculate game summary from move analyses
     */
    private fun calculateSummary(
        gameId: String,
        moves: List<MoveAnalysis>,
        tier: AnalysisTier,
    ): GameAnalysisSummary {
        val totalMoves = moves.size
        val stats = qualityClassifier.getMoveQualityStats(moves.map { it.moveQuality })

        val phaseAnalysis =
            moves
                .groupBy { it.phase.name }
                .mapValues { (phase, phaseMoves) ->
                    val phaseQualities = phaseMoves.map { it.moveQuality }
                    PhaseStats(
                        phase = GamePhase.valueOf(phase),
                        moves = phaseMoves.size,
                        accuracy = qualityClassifier.calculateAccuracy(phaseQualities),
                        avgCentipawnLoss = phaseMoves.map { it.centipawnLoss }.average(),
                    )
                }

        return GameAnalysisSummary(
            gameId = gameId,
            totalMoves = totalMoves,
            accuracy = stats.accuracy,
            bestMoves = stats.best,
            excellentMoves = stats.excellent,
            goodMoves = stats.good,
            bookMoves = stats.book,
            inaccuracies = stats.inaccuracies,
            mistakes = stats.mistakes,
            blunders = stats.blunders,
            avgCentipawnLoss = if (totalMoves > 0) moves.map { it.centipawnLoss }.average() else 0.0,
            phaseAnalysis = phaseAnalysis,
            analysisTier = tier.name,
            completedAt = Instant.now().toString(),
        )
    }
}
