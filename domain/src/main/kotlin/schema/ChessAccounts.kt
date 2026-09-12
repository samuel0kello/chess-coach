package schema

import org.jetbrains.exposed.dao.id.UUIDTable

object ChessAccounts : UUIDTable("chess_accounts", "chess_account_id") {
    val userId = reference("user_id", Users)
    val chessComUserName = varchar("chess_com_user_name", 64)
    val verified = bool("verified").default(false)
}
