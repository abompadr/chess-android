package com.chess.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chess.app.data.Profile
import com.chess.app.engine.ChessBoard
import com.chess.app.engine.KotlinChessEngine
import com.chess.app.engine.Move
import com.chess.app.engine.Piece
import com.chess.app.engine.Square
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class GameStatus { PLAYING, WHITE_WIN, BLACK_WIN, DRAW, ENGINE_ERROR }

data class GameState(
    val board: ChessBoard = ChessBoard(),
    val selected: Square? = null,
    val whiteTimeMs: Long = 0,
    val blackTimeMs: Long = 0,
    val status: GameStatus = GameStatus.PLAYING,
    val engineThinking: Boolean = false,
    val playerIsWhite: Boolean = true,
    val lastMove: Pair<Square, Square>? = null,
    val errorMessage: String = ""
)

class GameViewModel(app: Application) : AndroidViewModel(app) {

    private val engine = KotlinChessEngine()
    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state

    private var timerJob: Job? = null
    private var skillLevel: Int = 10

    fun startGame(p: Profile, playerIsWhite: Boolean = true) {
        skillLevel = p.skillLevel
        val timeMs = if (p.timeControlMinutes == 0) Long.MAX_VALUE / 2
                     else p.timeControlMinutes * 60_000L
        val board = ChessBoard()
        _state.value = GameState(
            board = board,
            whiteTimeMs = timeMs,
            blackTimeMs = timeMs,
            playerIsWhite = playerIsWhite
        )
        startTimer()
        if (!playerIsWhite) viewModelScope.launch { engineMove() }
    }

    fun onSquareTapped(sq: Square) {
        val s = _state.value
        if (s.status != GameStatus.PLAYING) return
        if (s.engineThinking) return
        if (s.board.whiteToMove != s.playerIsWhite) return

        val piece = s.board.get(sq)
        val isOwnPiece = if (s.playerIsWhite) piece > 0 else piece < 0

        if (s.selected == null) {
            if (isOwnPiece) _state.value = s.copy(selected = sq)
        } else {
            when {
                s.selected == sq  -> _state.value = s.copy(selected = null)
                isOwnPiece        -> _state.value = s.copy(selected = sq)
                else              -> applyPlayerMove(s.selected, sq)
            }
        }
    }

    private fun applyPlayerMove(from: Square, to: Square) {
        val s = _state.value
        val piece = Math.abs(s.board.get(from))
        val promotionRank = if (s.playerIsWhite) 7 else 0
        val isPromo = piece == Piece.PAWN && to.rank == promotionRank
        val uci = "${from}${to}${if (isPromo) "q" else ""}"
        s.board.applyUci(uci)
        _state.value = s.copy(selected = null, lastMove = from to to)
        checkGameOver()
        if (_state.value.status == GameStatus.PLAYING) {
            viewModelScope.launch { engineMove() }
        }
    }

    private suspend fun engineMove() {
        _state.value = _state.value.copy(engineThinking = true)
        try {
            // Deep-copy the board so the search never touches the UI-observed board
            val boardCopy = _state.value.board.deepCopy()
            val uci = withContext(Dispatchers.Default) {
                engine.bestMove(boardCopy, skillLevel)
            }
            if (uci.isNotEmpty()) {
                val move = Move.fromUci(uci)
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
            _state.value = _state.value.copy(
                engineThinking = false,
                status = GameStatus.ENGINE_ERROR,
                errorMessage = e.javaClass.simpleName + ": " + (e.message ?: "null")
            )
        }
    }

    private fun checkGameOver() {
        val s = _state.value
        if (s.board.halfMoveClock >= 100) {
            _state.value = s.copy(status = GameStatus.DRAW)
        }
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
    }
}
