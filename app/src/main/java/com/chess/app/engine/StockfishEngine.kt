package com.chess.app.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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
        // Android 10+ marks filesDir as noexec — use the native lib dir instead,
        // where libstockfish.so is installed by the package manager (always executable).
        val nativeLib = File(context.applicationInfo.nativeLibraryDir, "libstockfish.so")
        if (nativeLib.exists()) return nativeLib

        // Fallback: should not be reached on a correctly built APK
        throw IllegalStateException("Stockfish native library not found at ${nativeLib.absolutePath}")
    }
}
