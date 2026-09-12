package co.chesscoach

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChessComClientTest {
    @Test
    fun `normalizes Chesscom ECO URL to opening code`() {
        assertEquals(
            "Caro-Kann-Defense-2.Nf3-d5-3.exd5-cxd5",
            normalizeEco("https://www.chess.com/openings/Caro-Kann-Defense-2.Nf3-d5-3.exd5-cxd5")
        )
    }

    @Test
    fun `keeps an ECO code unchanged`() {
        assertEquals("C00", normalizeEco("C00"))
    }

    @Test
    fun `returns null for missing ECO`() {
        assertNull(normalizeEco(null))
    }
}
