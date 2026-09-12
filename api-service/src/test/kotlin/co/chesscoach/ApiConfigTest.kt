package co.chesscoach

import kotlin.test.Test
import kotlin.test.assertFailsWith

class ApiConfigTest {
    @Test
    fun `production rejects default and short jwt secrets`() {
        assertFailsWith<IllegalArgumentException> {
            ApiConfig(environment = "prod", jwtSecret = "local-development-secret")
        }
        assertFailsWith<IllegalArgumentException> {
            ApiConfig(environment = "production", jwtSecret = "too-short")
        }
    }
}
