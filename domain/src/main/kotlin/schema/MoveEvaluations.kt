package schema

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp

object MoveEvaluations : UUIDTable("move_evaluations", "move_evaluation_id") {
    val gameId = reference("game_id", Games) // References Games with VARCHAR(256)
    val ply = integer("ply")
    val sanMove = varchar("san_move", 32) // Increased from 16 to 32 for promotions like "e8=Q"
    val centipawnLoss = integer("centipawn_loss")
    val quality = varchar("quality", 16)
    val phase = varchar("phase", 16)
    val analysisTier = varchar("analysis_tier", 16)

    // Enhanced fields for Chess.com-like analysis
    val evaluationBefore = integer("evaluation_before").nullable()
    val evaluationAfter = integer("evaluation_after").nullable()
    val bestMove = varchar("best_move", 32).nullable() // Increased from 16 to 32
    val principalVariation = text("principal_variation").nullable()
    val moveQuality = varchar("move_quality", 16).nullable()
}

object GameAnalysisSummaries : UUIDTable("game_analysis_summaries", "summary_id") {
    val gameId = reference("game_id", Games) // References Games with VARCHAR(256)
    val totalMoves = integer("total_moves")
    val accuracy = double("accuracy") // PostgreSQL uses DOUBLE PRECISION
    val bestMoves = integer("best_moves")
    val excellentMoves = integer("excellent_moves")
    val goodMoves = integer("good_moves")
    val bookMoves = integer("book_moves")
    val inaccuracies = integer("inaccuracies")
    val mistakes = integer("mistakes")
    val blunders = integer("blunders")
    val avgCentipawnLoss = double("avg_centipawn_loss") // PostgreSQL uses DOUBLE PRECISION
    val analysisTier = varchar("analysis_tier", 16)
    val completedAt = timestamp("completed_at")
}

object AnalysisJobs : UUIDTable("analysis_jobs", "job_id") {
    val gameId = varchar("game_id", 256) // Increased from 128 to 256 for full URLs
    val status = varchar("status", 16)
    val tier = varchar("tier", 16)
    val progress = integer("progress")
    val totalMoves = integer("total_moves")
    val movesAnalyzed = integer("moves_analyzed")
    val createdAt = timestamp("created_at")
    val startedAt = timestamp("started_at").nullable()
    val completedAt = timestamp("completed_at").nullable()
}
