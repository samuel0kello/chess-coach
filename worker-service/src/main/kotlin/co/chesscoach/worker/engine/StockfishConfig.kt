package co.chesscoach.worker.engine

data class StockfishConfig(
    val executable: String = System.getenv("STOCKFISH_PATH") ?: "stockfish",
    val fastDepth: Int = environmentInt("STOCKFISH_FAST_DEPTH", 12),
    val deepDepth: Int = environmentInt("STOCKFISH_DEEP_DEPTH", 20),
    val threads: Int = environmentInt("STOCKFISH_THREADS", 1),
    val hashMb: Int = environmentInt("STOCKFISH_HASH_MB", 128)
) {
    init {
        require(fastDepth > 0) { "STOCKFISH_FAST_DEPTH must be greater than zero" }
        require(deepDepth >= fastDepth) {
            "STOCKFISH_DEEP_DEPTH must be greater than or equal to STOCKFISH_FAST_DEPTH"
        }
        require(threads > 0) { "STOCKFISH_THREADS must be greater than zero" }
        require(hashMb > 0) { "STOCKFISH_HASH_MB must be greater than zero" }
    }
}

private fun environmentInt(name: String, default: Int): Int =
    System.getenv(name)?.toIntOrNull() ?: default
