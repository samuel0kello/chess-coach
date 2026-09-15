package co.chesscoach.worker.chess

import domain.GamePhase
import domain.MoveQuality

/**
 * Move Quality Classification System
 * Implements Chess.com-style move quality classification
 */
class MoveQualityClassifier {
    /**
     * Classify move quality based on evaluation difference and context
     */
    fun classifyMove(
        evaluationBefore: Int,
        evaluationAfter: Int,
        bestMoveEvaluation: Int,
        actualMove: String,
        bestMove: String,
        phase: GamePhase,
        complexity: Int = 1,
    ): MoveQuality {
        val centipawnLoss = evaluationBefore - evaluationAfter
        val improvement = evaluationAfter - evaluationBefore
        val isBestMove = actualMove == bestMove

        return when {
            // Brilliant moves: significant position improvement
            improvement > 100 && !isBestMove -> MoveQuality.BRILLIANT

            improvement > 200 -> MoveQuality.BRILLIANT

            // Great moves: strong improvement or best move in complex position
            improvement > 50 && complexity > 7 -> MoveQuality.GREAT

            isBestMove && complexity > 5 -> MoveQuality.GREAT

            // Best moves: only move in position
            isBestMove && isOnlyBestMove(bestMoveEvaluation, evaluationBefore) -> MoveQuality.BEST

            // Excellent moves: good improvement
            improvement > 20 -> MoveQuality.EXCELLENT

            isBestMove && improvement > 10 -> MoveQuality.EXCELLENT

            // Good moves: maintains or slightly improves position
            improvement > 0 && !isBestMove -> MoveQuality.GOOD

            centipawnLoss == 0 && !isBestMove -> MoveQuality.GOOD

            // Book moves: opening theory moves
            phase == GamePhase.OPENING && isTheoreticalMove(actualMove) -> MoveQuality.BOOK

            // Blunders: major positional loss
            centipawnLoss > 300 -> MoveQuality.BLUNDER

            centipawnLoss > 200 && complexity > 7 -> MoveQuality.BLUNDER

            // Mistakes: significant positional loss
            centipawnLoss > 100 -> MoveQuality.MISTAKE

            centipawnLoss > 50 && complexity > 5 -> MoveQuality.MISTAKE

            // Inaccuracies: minor positional loss
            centipawnLoss > 30 -> MoveQuality.INACCURACY

            centipawnLoss > 10 && complexity > 5 -> MoveQuality.INACCURACY

            // Default to good for unclear cases
            else -> MoveQuality.GOOD
        }
    }

    /**
     * Check if a move is the only best move in the position
     */
    private fun isOnlyBestMove(
        bestMoveEvaluation: Int,
        evaluationBefore: Int,
    ): Boolean = (bestMoveEvaluation - evaluationBefore) > 50

    /**
     * Check if a move is a theoretical opening move
     */
    private fun isTheoreticalMove(move: String): Boolean {
        val commonOpenings =
            setOf(
                "e4",
                "d4",
                "c4",
                "Nf3",
                "g3",
                "f4",
                "e3",
                "d3",
                "c3",
                "Nc3",
                "e5",
                "d5",
                "c5",
                "Nf6",
                "g6",
                "f5",
                "e6",
                "d6",
                "c6",
                "Nc6",
            )
        return commonOpenings.contains(move)
    }

    /**
     * Calculate move complexity based on position characteristics
     */
    fun calculateComplexity(
        evaluation: Int,
        phase: GamePhase,
        moveNumber: Int,
    ): Int =
        when {
            phase == GamePhase.MIDDLEGAME && moveNumber > 10 -> 8
            phase == GamePhase.MIDDLEGAME && evaluation > 50 -> 7
            phase == GamePhase.MIDDLEGAME && evaluation < -50 -> 7
            phase == GamePhase.ENDGAME -> 6
            phase == GamePhase.OPENING -> 3
            else -> 5
        }

    /**
     * Calculate accuracy for a set of moves
     */
    fun calculateAccuracy(moves: List<MoveQuality>): Double {
        if (moves.isEmpty()) return 0.0

        val goodMoves =
            moves.count {
                it in
                    listOf(
                        MoveQuality.BRILLIANT,
                        MoveQuality.GREAT,
                        MoveQuality.BEST,
                        MoveQuality.EXCELLENT,
                        MoveQuality.GOOD,
                        MoveQuality.BOOK,
                    )
            }

        return (goodMoves.toDouble() / moves.size) * 100
    }

    /**
     * Get move quality statistics
     */
    fun getMoveQualityStats(moves: List<MoveQuality>): MoveQualityStats =
        MoveQualityStats(
            totalMoves = moves.size,
            brilliant = moves.count { it == MoveQuality.BRILLIANT },
            great = moves.count { it == MoveQuality.GREAT },
            best = moves.count { it == MoveQuality.BEST },
            excellent = moves.count { it == MoveQuality.EXCELLENT },
            good = moves.count { it == MoveQuality.GOOD },
            book = moves.count { it == MoveQuality.BOOK },
            inaccuracies = moves.count { it == MoveQuality.INACCURACY },
            mistakes = moves.count { it == MoveQuality.MISTAKE },
            blunders = moves.count { it == MoveQuality.BLUNDER },
            unknown = moves.count { it == MoveQuality.UNKNOWN },
        )
}

data class MoveQualityStats(
    val totalMoves: Int,
    val brilliant: Int,
    val great: Int,
    val best: Int,
    val excellent: Int,
    val good: Int,
    val book: Int,
    val inaccuracies: Int,
    val mistakes: Int,
    val blunders: Int,
    val unknown: Int,
) {
    val accuracy: Double
        get() {
            if (totalMoves == 0) return 0.0
            val goodMoves = brilliant + great + best + excellent + good + book
            return (goodMoves.toDouble() / totalMoves) * 100
        }
}
