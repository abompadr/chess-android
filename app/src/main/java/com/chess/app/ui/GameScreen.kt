package com.chess.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chess.app.data.Profile
import com.chess.app.engine.Piece
import com.chess.app.engine.Square
import java.util.Locale
import kotlin.math.min

private val LIGHT_SQ   = Color(0xFFF0D9B5)
private val DARK_SQ    = Color(0xFFB58863)
private val SELECT_COL = Color(0x9900CC44)
private val LAST_COL   = Color(0x88CCCC00)

// Unicode chess pieces
private val WHITE_PIECES = mapOf(
    Piece.KING   to "♔", Piece.QUEEN  to "♕", Piece.ROOK   to "♖",
    Piece.BISHOP to "♗", Piece.KNIGHT to "♘", Piece.PAWN   to "♙"
)
private val BLACK_PIECES = mapOf(
    Piece.KING   to "♚", Piece.QUEEN  to "♛", Piece.ROOK   to "♜",
    Piece.BISHOP to "♝", Piece.KNIGHT to "♞", Piece.PAWN   to "♟"
)

@Composable
fun GameScreen(profile: Profile, onBack: () -> Unit, vm: GameViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    LaunchedEffect(profile) { vm.startGame(profile) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("← Back", color = Color(0xFF8888AA)) }
            Spacer(Modifier.weight(1f))
            Text(profile.name, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { vm.resign() }) { Text("Resign", color = Color(0xFFE74C3C)) }
        }

        // Black timer (top)
        TimerBar(state.blackTimeMs, state.board.whiteToMove == false && state.status == GameStatus.PLAYING, "Black")

        // Board
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            val size = min(maxWidth.value, maxHeight.value).dp
            ChessboardView(
                state = state,
                modifier = Modifier.size(size),
                onSquare = { vm.onSquareTapped(it) }
            )
        }

        // White timer (bottom)
        TimerBar(state.whiteTimeMs, state.board.whiteToMove && state.status == GameStatus.PLAYING, "White")

        if (state.engineThinking) {
            Text("Thinking...", color = Color(0xFF8888AA), fontSize = 12.sp, modifier = Modifier.padding(4.dp))
        } else {
            Spacer(Modifier.height(20.dp))
        }
    }

    // Game-over overlay
    if (state.status != GameStatus.PLAYING) {
        val msg = when (state.status) {
            GameStatus.WHITE_WIN    -> "White wins!"
            GameStatus.BLACK_WIN    -> "Black wins!"
            GameStatus.ENGINE_ERROR -> "Engine failed to load"
            else                    -> "Draw"
        }
        GameOverOverlay(msg, onBack)
    }
}

@Composable
private fun TimerBar(timeMs: Long, active: Boolean, label: String) {
    val unlimited = timeMs > 30 * 60_000L * 10
    val display = if (unlimited) "∞" else formatTime(timeMs)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(if (active) Color(0xFF2A4A2A) else Color(0xFF2A2A4A), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 14.sp)
        Text(display, color = if (active) Color(0xFF4CAF50) else Color.White,
            fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return String.format(Locale.US, "%d:%02d", m, s)
}

@Composable
private fun ChessboardView(state: GameState, modifier: Modifier, onSquare: (Square) -> Unit) {
    BoxWithConstraints(modifier = modifier) {
        val cellSize = maxWidth / 8
        Column {
            for (r in 7 downTo 0) {
                Row {
                    for (f in 0..7) {
                        val sq = Square(f, r)
                        val isLight = (r + f) % 2 == 1
                        val isSelected = state.selected == sq
                        val isLastMove = state.lastMove?.let { it.first == sq || it.second == sq } == true
                        val piece = state.board.get(sq)

                        Box(
                            modifier = Modifier
                                .size(cellSize)
                                .background(
                                    when {
                                        isSelected  -> SELECT_COL
                                        isLastMove  -> LAST_COL
                                        isLight     -> LIGHT_SQ
                                        else        -> DARK_SQ
                                    }
                                )
                                .clickable { onSquare(sq) },
                            contentAlignment = Alignment.Center
                        ) {
                            val symbol = when {
                                piece > 0 -> WHITE_PIECES[piece]
                                piece < 0 -> BLACK_PIECES[-piece]
                                else -> null
                            }
                            if (symbol != null) {
                                Text(
                                    text = symbol,
                                    fontSize = (cellSize.value * 0.72f).sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = (cellSize.value * 0.72f).sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameOverOverlay(message: String, onBack: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xCC000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onBack) { Text("Back to Profiles") }
        }
    }
}
