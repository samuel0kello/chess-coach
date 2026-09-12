package co.chesscoach.worker.engine

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit

class StockfishEngine(
    private val config: StockfishConfig
) : ChessEngine, Closeable {
    private val process = ProcessBuilder(config.executable)
        .start()
    private val input = BufferedReader(InputStreamReader(process.inputStream))
    private val output = BufferedWriter(OutputStreamWriter(process.outputStream))
    private val errorReader = BufferedReader(InputStreamReader(process.errorStream))

    init {
        Thread {
            errorReader.forEachLine { line ->
                System.err.println("[stockfish] $line")
            }
        }.apply {
            isDaemon = true
            start()
        }
        send("uci")
        readUntil("uciok")
        send("setoption name Threads value ${config.threads}")
        send("setoption name Hash value ${config.hashMb}")
        send("isready")
        readUntil("readyok")
    }

    @Synchronized
    override fun analyze(fen: String, depth: Int): EngineAnalysis {
        require(fen.isNotBlank()) { "FEN must not be blank" }
        require(depth > 0) { "Analysis depth must be greater than zero" }

        send("position fen ${fen.trim()}")
        send("go depth $depth")

        var score: EngineScore? = null
        var principalVariation = emptyList<String>()
        var bestMove: String? = null

        while (true) {
            val line = readLine()
            when {
                line == "bestmove" || line.startsWith("bestmove ") -> {
                    bestMove = line.removePrefix("bestmove").trim().split(' ').firstOrNull()
                    break
                }
                line.startsWith("info ") -> {
                    UciParser.parseScore(line)?.let { score = it }
                    UciParser.parsePrincipalVariation(line)?.let { principalVariation = it }
                }
            }
        }

        return EngineAnalysis(
            bestMove = requireNotNull(bestMove) { "Stockfish returned no best move" },
            score = score,
            principalVariation = principalVariation
        )
    }

    override fun close() {
        if (!process.isAlive) return
        send("quit")
        if (!process.waitFor(2, TimeUnit.SECONDS)) {
            process.destroy()
        }
        input.close()
        output.close()
        errorReader.close()
    }

    private fun send(command: String) {
        output.write(command)
        output.newLine()
        output.flush()
    }

    private fun readUntil(expected: String) {
        while (readLine() != expected) {
            // UCI permits informational lines before the expected handshake response.
        }
    }

    private fun readLine(): String =
        input.readLine() ?: error("Stockfish exited unexpectedly")

}

object UciParser {
    fun parseScore(line: String): EngineScore? {
        val tokens = line.trim().split(Regex("\\s+"))
        val scoreIndex = tokens.indexOf("score")
        if (scoreIndex < 0 || scoreIndex + 2 >= tokens.size) return null
        return when (tokens[scoreIndex + 1]) {
            "cp" -> tokens[scoreIndex + 2].toIntOrNull()?.let(EngineScore::Centipawns)
            "mate" -> tokens[scoreIndex + 2].toIntOrNull()?.let(EngineScore::Mate)
            else -> null
        }
    }

    fun parsePrincipalVariation(line: String): List<String>? {
        val tokens = line.trim().split(Regex("\\s+"))
        val pvIndex = tokens.indexOf("pv")
        return if (pvIndex < 0) null else tokens.drop(pvIndex + 1).takeIf { it.isNotEmpty() }
    }
}
