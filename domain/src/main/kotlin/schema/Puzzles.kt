package schema

import org.jetbrains.exposed.dao.id.UUIDTable

object Puzzles : UUIDTable("puzzles", "puzzle_id") {
    val gameId = reference("game_id", Games)
    val fen = varchar("fen", 128)
    val theme = varchar("theme", 32)
    val difficulty = integer("difficulty")
}
