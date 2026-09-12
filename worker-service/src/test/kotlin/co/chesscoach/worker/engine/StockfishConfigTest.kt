package co.chesscoach.worker.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StockfishConfigTest {
    @Test
    fun `defaults are safe and deep depth is greater than fast`() {
        val config = StockfishConfig(executable = "stockfish", fastDepth = 8, deepDepth = 16, threads = 2, hashMb = 64)
        assertEquals(8, config.fastDepth)
        assertEquals(16, config.deepDepth)
    }

    @Test
    fun `invalid values are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            StockfishConfig(fastDepth = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            StockfishConfig(fastDepth = 20, deepDepth = 10)
        }
    }
}
