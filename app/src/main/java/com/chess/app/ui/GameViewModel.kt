package com.chess.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chess.app.data.Profile
import com.chess.app.engine.ChessBoard
import com.chess.app.engine.Square
import com.chess.app.engine.StockfishEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class GameStatus { PLAYING, WHITE_WIN, BLACK_WIN, DRAW, ENGINE_ERROR }

data class GameState(
    val board: ChessBoard = ChessBoard(),
    val selected: Square? = null,
    val whiteTimeMs: Long = 0,
    val blackTimeMs: Long = 0,
    val status: GameStatus = GameStatus.PLAYING,
    val engineThinking: Boolean = false,
    val playerIsWhite: Boolean = true,
    val lastMove: Pair<Square, Square>? = null
)

class GameViewModel(app: Application) : AndroidViewModel(app) {

    private val engine = StockfishEngine(app)
    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state

    private var timerJob: Job? = null
    private var profile: Profile = Profile(name = "Player")

    fun startGame(p: Profile, playerIsWhite: Boolean = true) {
        profile = p
        val timeMs = if (p.timeControlMinutes == 0) Long.MAX_VALUE / 2
                     else p.timeControlMinutes * 60_000L
        val board = ChessBoard()
        _state.value = GameState(
            board = board,
            whiteTimeMs = timeMs,
            blackTimeMs = timeMs,
            playerIsWhite = playerIsWhite
        )
        viewModelScope.launch {
            try {
                engine.start()
                engine.setSkillLevel(p.skillLevel)
                startTimer()
                if (!playerIsWhite) engineMove()
            } catch (e: Exception) {
                _state.value = _state.value.copy(status = GameStatus.ENGINE_ERROR)
            }
        }
    }

    fun onSquareTapped(sq: Square) {
        val s = _state.value
        if (s.status != GameStatus.PLAYING) return
        if (s.engineThinking) return
        val isPlayerTurn = s.board.whiteToMove == s.playerIsWhite

        if (!isPlayerTurn) return

        val piece = s.board.get(sq)
        val isOwnPiece = if (s.playerIsWhite) piece > 0 else piece < 0

        if (s.selected == null) {
            if (isOwnPiece) _state.value = s.copy(selected = sq)
        } else {
            if (s.selected == sq) {
                _state.value = s.copy(selected = null)
            } else if (isOwnPiece) {
                _state.value = s.copy(selected = sq)
            } else {
                applyPlayerMove(s.selected, sq)
            }
        }
    }

    private fun applyPlayerMove(from: Square, to: Square) {
        val s = _state.value
        // Simple promotion: always promote to queen
        val piece = Math.abs(s.board.get(from))
        val promotionRank = if (s.playerIsWhite) 7 else 0
        val promo = if (piece == com.chess.app.engine.Piece.PAWN && to.rank == promotionRank)
            com.chess.app.engine.Piece.QUEEN else com.chess.app.engine.Piece.EMPTY

        val uci = "${from}${to}${if (promo != com.chess.app.engine.Piece.EMPTY) "q" else ""}"
        s.board.applyUci(uci)
        _state.value = s.copy(selected = null, lastMove = from to to)

        checkGameOver()
        if (_state.value.status == GameStatus.PLAYING) {
            viewModelScope.launch { engineMove() }
        }
    }

    private suspend fun engineMove() {
        val s = _state.value
        _state.value = s.copy(engineThinking = true)
        try {
            val thinkMs = 1500
            val uci = engine.getBestMove(thinkMs)
            if (uci.isNotEmpty() && uci != "(none)") {
                val move = com.chess.app.engine.Move.fromUci(uci)
                _state.value.board.applyUci(uci)
                _state.value = _state.value.copy(
                    engineThinking = false,
                    lastMove = move.from to move.to
                )
                checkGameOver()
            } else {
                _state.value = _state.value.copy(engineThinking = false)
            }
        } catch (e: Exception) {
            _state.value = _state.value.copy(engineThinking = false, status = GameStatus.ENGINE_ERROR)
        }
    }

    private fun checkGameOver() {
        val s = _state.value
        if (s.board.halfMoveClock >= 100) {
            _state.value = s.copy(status = GameStatus.DRAW)
        }
        // Basic flag check on timers
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(100)
                val s = _state.value
                if (s.status != GameStatus.PLAYING) break
                if (s.board.whiteToMove) {
                    val newTime = s.whiteTimeMs - 100
                    if (newTime <= 0) { _state.value = s.copy(whiteTimeMs = 0, status = GameStatus.BLACK_WIN); break }
                    _state.value = s.copy(whiteTimeMs = newTime)
                } else {
                    val newTime = s.blackTimeMs - 100
                    if (newTime <= 0) { _state.value = s.copy(blackTimeMs = 0, status = GameStatus.WHITE_WIN); break }
                    _state.value = s.copy(blackTimeMs = newTime)
                }
            }
        }
    }

    fun resign() {
        val s = _state.value
        _state.value = s.copy(status = if (s.playerIsWhite) GameStatus.BLACK_WIN else GameStatus.WHITE_WIN)
        timerJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        engine.stop()
    }
}
