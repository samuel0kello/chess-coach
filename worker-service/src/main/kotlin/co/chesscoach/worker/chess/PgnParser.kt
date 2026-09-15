package co.chesscoach.worker.chess

/**
 * Simple PGN Parser for Chess.com/Lichess format
 * Handles standard PGN.
 * exaple pgn:
 * chess.com
 * [Event "Live Chess"]
 * [Site "Chess.com"]
 * [Date "2026.09.14"]
 * [Round "?"]
 * [White "sammuelokello"]
 * [Black "dionisovinicius"]
 * [Result "0-1"]
 * [TimeControl "60"]
 * [WhiteElo "583"]
 * [BlackElo "619"]
 * [Termination "dionisovinicius won on time"]
 * [ECO "A01"]
 * [EndTime "5:19:45 GMT+0000"]
 * [Link "https://www.chess.com/game/live/174447707176?move=0"]
 *
 * 1. b3 c5 2. Bb2 e6 3. e3 d6 4. f3 Nc6 5. g4 Be7 6. f4 e5 7. h3 exf4 8. exf4 Bh4+
 * 9. Ke2 Qe7+ 10. Be5 dxe5 11. fxe5 Qxe5+ 12. Kf3 Qxa1 13. c3 Qxa2 14. Bd3 Qa5 15.
 * Ne2 Nf6 16. Kg2 O-O 17. Nf4 b6 18. Be2 Bb7 19. Bf3 Bg5 20. Nd5 Ne7 21. Nxe7+ Kh8
 * 22. Bxb7 Rab8 23. Nc6 Rxb7 24. Nxa5 bxa5 25. b4 Ne4 26. bxa5 Nxc3 27. dxc3 Rb2+
 * 0-1
 */
class PgnParser {
    /**
     * Parse PGN and extract moves with FEN positions
     */
    fun parsePgn(pgn: String): PgnParseResult =
        try {
            val lines = pgn.lines()
            val metadata = extractMetadata(lines)
            val moves = extractMoves(pgn)

            PgnParseResult.Success(
                gameId = metadata["Event"] ?: generateGameId(metadata),
                whitePlayer = metadata["White"] ?: "White",
                blackPlayer = metadata["Black"] ?: "Black",
                event = metadata["Event"],
                site = metadata["Site"],
                date = metadata["Date"],
                result = parseResult(metadata["Result"]),
                eco = metadata["ECO"],
                opening = metadata["Opening"],
                moves = moves,
                pgn = pgn,
            )
        } catch (e: Exception) {
            PgnParseResult.Error("PGN parsing error: ${e.message}")
        }

    /**
     * Extract metadata from PGN headers
     */
    private fun extractMetadata(lines: List<String>): Map<String, String> {
        val metadata = mutableMapOf<String, String>()
        for (line in lines) {
            if (line.startsWith("[") && line.endsWith("]")) {
                val match = Regex("\\[(\\w+)\\s+\"(.*)\"\\]").find(line)
                if (match != null) {
                    metadata[match.groupValues[1]] = match.groupValues[2]
                }
            }
        }
        return metadata
    }

    /**
     * Extract moves from PGN (simplified - basic SAN notation)
     */
    private fun extractMoves(pgn: String): List<ParsedMove> {
        val moves = mutableListOf<ParsedMove>()

        // Skip all header lines (lines starting with '[') and extract only move text
        val moveText =
            pgn
                .lines()
                .dropWhile { it.startsWith("[") } // Skip all header lines
                .joinToString(" ")
                .replace(Regex("\\{[^}]*\\}"), "") // Remove comments
                .replace(Regex("\\([^)]*\\)"), "") // Remove variations
                .replace(Regex("\\d+\\."), "") // Remove move numbers
                .replace(Regex("1-0|0-1|1/2-1/2|\\*"), "") // Remove result

        val moveList = moveText.split(Regex("\\s+")).filter { it.isNotEmpty() && !it.startsWith("[") }

        var currentFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

        moveList.forEachIndexed { index, san ->
            // Skip if this looks like a header (starts with '[')
            if (san.startsWith("[")) return@forEachIndexed

            val fenBefore = currentFen
            val fenAfter = applyMoveToFen(currentFen, san)

            moves.add(
                ParsedMove(
                    san = san,
                    fenBefore = fenBefore,
                    fenAfter = fenAfter,
                    moveNumber = (index / 2) + 1,
                    isWhite = index % 2 == 0,
                ),
            )

            currentFen = fenAfter
        }

        return moves
    }

    /**
     * Apply a move to a FEN string (simplified - tracks basic piece movement)
     * Note: This is a simplified version. For full chess rules, you'd need a complete chess engine.
     */
    private fun applyMoveToFen(
        fen: String,
        move: String,
    ): String {
        // This is a placeholder - in a real implementation, you'd need:
        // 1. Parse the move (e.g., "e4", "Nf3", "Bxe5")
        // 2. Update the board representation
        // 3. Handle castling, en passant, promotion
        // 4. Update castling rights, en passant square, half-move counter, etc.

        // For now, return the same FEN as a placeholder
        // In production, you'd integrate with Stockfish or a chess library here
        return fen
    }

    /**
     * Parse game result
     */
    private fun parseResult(result: String?): GameResult =
        when (result) {
            "1-0" -> GameResult.WHITE_WINS
            "0-1" -> GameResult.BLACK_WINS
            "1/2-1/2" -> GameResult.DRAW
            else -> GameResult.ONGOING
        }

    /**
     * Generate a game ID from metadata
     */
    private fun generateGameId(metadata: Map<String, String>): String {
        val white = metadata["White"] ?: "White"
        val black = metadata["Black"] ?: "Black"
        val date = metadata["Date"] ?: "unknown"
        return "${white}_vs_${black}_$date".hashCode().toString()
    }
}

// Data classes for PGN parsing results

sealed class PgnParseResult {
    data class Success(
        val gameId: String,
        val whitePlayer: String,
        val blackPlayer: String,
        val event: String?,
        val site: String?,
        val date: String?,
        val result: GameResult,
        val eco: String?,
        val opening: String?,
        val moves: List<ParsedMove>,
        val pgn: String,
    ) : PgnParseResult()

    data class Error(
        val message: String,
    ) : PgnParseResult()
}

data class ParsedMove(
    val san: String,
    val fenBefore: String,
    val fenAfter: String,
    val moveNumber: Int,
    val isWhite: Boolean,
)

enum class GameResult {
    WHITE_WINS,
    BLACK_WINS,
    DRAW,
    ONGOING,
}
