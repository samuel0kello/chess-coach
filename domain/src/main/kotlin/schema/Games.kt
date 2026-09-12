package schema

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.javatime.timestamp

object Games : IdTable<String>("games") {
    override val id: Column<EntityID<String>> = varchar("game_id", 128).entityId()
    val chessAccountId = reference("chess_account_id", ChessAccounts)
    val pgn = text("pgn")
    val playedAsWhite = bool("played_as_white")
    val timeClass = varchar("time_class", 32)
    val openingEco = varchar("opening_eco", 255).nullable()
    val termination = varchar("termination", 32)
    val endTime = timestamp("end_time")
    override val primaryKey = PrimaryKey(id)
}
