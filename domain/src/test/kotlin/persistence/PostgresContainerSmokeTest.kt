package persistence

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.testcontainers.containers.PostgreSQLContainer
import kotlin.test.assertTrue

@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
class PostgresContainerSmokeTest {
    @Test
    fun `postgres container accepts application connection`() {
        PostgreSQLContainer("postgres:16-alpine").use { postgres ->
            postgres.start()
            val config = DatabaseConfig(postgres.jdbcUrl, postgres.username, postgres.password)
            assertTrue(config.connect().url.startsWith("jdbc:postgresql://"))
        }
    }
}
