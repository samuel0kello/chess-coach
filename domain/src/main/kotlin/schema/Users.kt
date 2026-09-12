package schema

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

object Users : UUIDTable("users", "user_id") {
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)

    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
}
