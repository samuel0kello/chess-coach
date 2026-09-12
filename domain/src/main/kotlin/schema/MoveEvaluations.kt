package schema

import org.jetbrains.exposed.dao.id.UUIDTable

object MoveEvaluations : UUIDTable("move_evaluations", "move_evaluation_id") {
    val gameId = reference("game_id", Games)
    val ply = integer("ply")
    val sanMove = varchar("san_move", 16)
    val centipawnLoss = integer("centipawn_loss")
    val quality = varchar("quality", 16)
    val phase = varchar("phase", 16)
    val analysisTier = varchar("analysis_tier", 16)
}
