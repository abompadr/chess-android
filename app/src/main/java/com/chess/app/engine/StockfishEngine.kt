package com.chess.app.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

class StockfishEngine(private val context: Context) {

    private var process: Process? = null
    private var output: java.io.BufferedReader? = null
    private var input: java.io.PrintWriter? = null

    suspend fun start() = withContext(Dispatchers.IO) {
        val binary = extractBinary()
        process = ProcessBuilder(binary.absolutePath)
            .redirectErrorStream(true)
            .start()
        output = process!!.inputStream.bufferedReader()
        input = java.io.PrintWriter(process!!.outputStream, true)
        send("uci")
        waitFor("uciok")
        send("isready")
        waitFor("readyok")
    }

    fun setSkillLevel(level: Int) {
        send("setoption name Skill Level value $level")
    }

    fun setPosition(fen: String, moves: List<String> = emptyList()) {
        val movePart = if (moves.isEmpty()) "" else " moves ${moves.joinToString(" ")}"
        send("position fen $fen$movePart")
    }

    suspend fun getBestMove(timeLimitMs: Int = 2000): String = withContext(Dispatchers.IO) {
        send("go movetime $timeLimitMs")
        var best = ""
        while (true) {
            val line = output?.readLine() ?: break
            if (line.startsWith("bestmove")) {
                best = line.split(" ")[1]
                break
            }
        }
        best
    }

    fun stop() {
        send("quit")
        process?.destroy()
    }

    private fun send(cmd: String) {
        input?.println(cmd)
    }

    private fun waitFor(token: String) {
        while (true) {
            val line = output?.readLine() ?: break
            if (line.contains(token)) break
        }
    }

    private fun extractBinary(): File {
        val dest = File(context.filesDir, "stockfish")
        if (!dest.exists() || dest.length() == 0L) {
            dest.delete()
            context.assets.open("stockfish").use { input: InputStream ->
                dest.outputStream().use { input.copyTo(it) }
            }
            dest.setExecutable(true)
        }
        if (!dest.canExecute()) dest.setExecutable(true)
        return dest
    }
}
