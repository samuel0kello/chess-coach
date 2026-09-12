package co.chesscoach.worker.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UciParserTest {
    @Test
    fun `parses centipawn and mate scores`() {
        assertEquals(EngineScore.Centipawns(34), UciParser.parseScore("info depth 12 score cp 34 pv e2e4"))
        assertEquals(EngineScore.Mate(3), UciParser.parseScore("info score mate 3"))
    }

    @Test
    fun `parses principal variation with arbitrary whitespace`() {
        assertEquals(listOf("e2e4", "e7e5", "g1f3"), UciParser.parsePrincipalVariation("info   depth 8 pv e2e4 e7e5 g1f3"))
        assertNull(UciParser.parseScore("info depth 8 nodes 100"))
    }
}
