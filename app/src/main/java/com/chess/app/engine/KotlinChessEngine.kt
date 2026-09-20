package com.chess.app.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// A simple but playable alpha-beta chess engine in pure Kotlin.
// No external process needed — runs entirely in-process.

private const val INF = 100_000
private const val MATE = 99_000

// Piece values (centipawns)
private val PIECE_VALUE = intArrayOf(0, 100, 320, 330, 500, 900, 20000)

// Piece-square tables (from white's perspective, rank 0 = white's back rank)
private val PST_PAWN = intArrayOf(
     0,  0,  0,  0,  0,  0,  0,  0,
    50, 50, 50, 50, 50, 50, 50, 50,
    10, 10, 20, 30, 30, 20, 10, 10,
     5,  5, 10, 25, 25, 10,  5,  5,
     0,  0,  0, 20, 20,  0,  0,  0,
     5, -5,-10,  0,  0,-10, -5,  5,
     5, 10, 10,-20,-20, 10, 10,  5,
     0,  0,  0,  0,  0,  0,  0,  0
)
private val PST_KNIGHT = intArrayOf(
    -50,-40,-30,-30,-30,-30,-40,-50,
    -40,-20,  0,  0,  0,  0,-20,-40,
    -30,  0, 10, 15, 15, 10,  0,-30,
    -30,  5, 15, 20, 20, 15,  5,-30,
    -30,  0, 15, 20, 20, 15,  0,-30,
    -30,  5, 10, 15, 15, 10,  5,-30,
    -40,-20,  0,  5,  5,  0,-20,-40,
    -50,-40,-30,-30,-30,-30,-40,-50
)
private val PST_BISHOP = intArrayOf(
    -20,-10,-10,-10,-10,-10,-10,-20,
    -10,  0,  0,  0,  0,  0,  0,-10,
    -10,  0,  5, 10, 10,  5,  0,-10,
    -10,  5,  5, 10, 10,  5,  5,-10,
    -10,  0, 10, 10, 10, 10,  0,-10,
    -10, 10, 10, 10, 10, 10, 10,-10,
    -10,  5,  0,  0,  0,  0,  5,-10,
    -20,-10,-10,-10,-10,-10,-10,-20
)
private val PST_ROOK = intArrayOf(
     0,  0,  0,  0,  0,  0,  0,  0,
     5, 10, 10, 10, 10, 10, 10,  5,
    -5,  0,  0,  0,  0,  0,  0, -5,
    -5,  0,  0,  0,  0,  0,  0, -5,
    -5,  0,  0,  0,  0,  0,  0, -5,
    -5,  0,  0,  0,  0,  0,  0, -5,
    -5,  0,  0,  0,  0,  0,  0, -5,
     0,  0,  0,  5,  5,  0,  0,  0
)
private val PST_QUEEN = intArrayOf(
    -20,-10,-10, -5, -5,-10,-10,-20,
    -10,  0,  0,  0,  0,  0,  0,-10,
    -10,  0,  5,  5,  5,  5,  0,-10,
     -5,  0,  5,  5,  5,  5,  0, -5,
      0,  0,  5,  5,  5,  5,  0, -5,
    -10,  5,  5,  5,  5,  5,  0,-10,
    -10,  0,  5,  0,  0,  0,  0,-10,
    -20,-10,-10, -5, -5,-10,-10,-20
)
private val PST_KING_MID = intArrayOf(
    -30,-40,-40,-50,-50,-40,-40,-30,
    -30,-40,-40,-50,-50,-40,-40,-30,
    -30,-40,-40,-50,-50,-40,-40,-30,
    -30,-40,-40,-50,-50,-40,-40,-30,
    -20,-30,-30,-40,-40,-30,-30,-20,
    -10,-20,-20,-20,-20,-20,-20,-10,
     20, 20,  0,  0,  0,  0, 20, 20,
     20, 30, 10,  0,  0, 10, 30, 20
)

private val PST = arrayOf(null, PST_PAWN, PST_KNIGHT, PST_BISHOP, PST_ROOK, PST_QUEEN, PST_KING_MID)

// Returns PST bonus for a piece at a square, always from white's perspective
private fun pstBonus(piece: Int, rank: Int, file: Int, isWhite: Boolean): Int {
    val pst = PST[piece] ?: return 0
    val r = if (isWhite) (7 - rank) else rank
    return pst[r * 8 + file]
}

// Simple move representation for the engine
data class EngineMove(
    val fromRank: Int, val fromFile: Int,
    val toRank: Int,   val toFile: Int,
    val promotion: Int = Piece.EMPTY
) {
    fun toUci(): String {
        val f = "abcdefgh"
        val promo = if (promotion != Piece.EMPTY) "pnbrqk"[promotion] else ' '
        return "${f[fromFile]}${fromRank+1}${f[toFile]}${toRank+1}${if (promotion != Piece.EMPTY) promo else ""}"
    }
}

class KotlinChessEngine {

    // Find best move at given skill level (0-20 mapped to depth 1-5)
    fun bestMove(board: ChessBoard, skillLevel: Int): String {
        val depth = when {
            skillLevel <= 3  -> 1
            skillLevel <= 7  -> 2
            skillLevel <= 12 -> 3
            skillLevel <= 17 -> 4
            else             -> 5
        }
        val moves = generateMoves(board)
        if (moves.isEmpty()) return ""
        var bestScore = -INF
        var bestMove = moves.first()
        for (move in moves) {
            val snapshot = cloneBoard(board)
            applyMove(board, move)
            val score = -alphaBeta(board, depth - 1, -INF, INF)
            restoreBoard(board, snapshot)
            if (score > bestScore) {
                bestScore = score
                bestMove = move
            }
        }
        return bestMove.toUci()
    }

    private fun alphaBeta(board: ChessBoard, depth: Int, alpha: Int, beta: Int): Int {
        if (depth == 0) return evaluate(board)
        val moves = generateMoves(board)
        if (moves.isEmpty()) {
            return if (inCheck(board, board.whiteToMove)) -MATE else 0
        }
        var a = alpha
        for (move in moves) {
            val snapshot = cloneBoard(board)
            applyMove(board, move)
            val score = -alphaBeta(board, depth - 1, -beta, -a)
            restoreBoard(board, snapshot)
            if (score >= beta) return beta
            if (score > a) a = score
        }
        return a
    }

    // Static evaluation (positive = good for side to move)
    private fun evaluate(board: ChessBoard): Int {
        var score = 0
        for (r in 0..7) for (f in 0..7) {
            val p = board.get(r, f)
            if (p == Piece.EMPTY) continue
            val isWhite = p > 0
            val abs = abs(p)
            val mat = PIECE_VALUE[abs] + pstBonus(abs, r, f, isWhite)
            score += if (isWhite) mat else -mat
        }
        return if (board.whiteToMove) score else -score
    }

    // ── Move generation ───────────────────────────────────────────────────────

    private fun generateMoves(board: ChessBoard): List<EngineMove> {
        val moves = mutableListOf<EngineMove>()
        for (r in 0..7) for (f in 0..7) {
            val p = board.get(r, f)
            if (p == Piece.EMPTY) continue
            if (board.whiteToMove != (p > 0)) continue
            when (abs(p)) {
                Piece.PAWN   -> genPawnMoves(board, r, f, moves)
                Piece.KNIGHT -> genLeaperMoves(board, r, f, KNIGHT_DELTAS, moves)
                Piece.BISHOP -> genSlidingMoves(board, r, f, BISHOP_DIRS, moves)
                Piece.ROOK   -> genSlidingMoves(board, r, f, ROOK_DIRS, moves)
                Piece.QUEEN  -> genSlidingMoves(board, r, f, QUEEN_DIRS, moves)
                Piece.KING   -> genKingMoves(board, r, f, moves)
            }
        }
        // Filter moves that leave own king in check
        return moves.filter { move ->
            val snap = cloneBoard(board)
            applyMove(board, move)
            val legal = !inCheck(board, !board.whiteToMove)
            restoreBoard(board, snap)
            legal
        }
    }

    private fun genPawnMoves(board: ChessBoard, r: Int, f: Int, moves: MutableList<EngineMove>) {
        val white = board.whiteToMove
        val dir = if (white) 1 else -1
        val startRank = if (white) 1 else 6
        val promoRank = if (white) 7 else 0

        // Forward
        val nr = r + dir
        if (nr in 0..7 && board.get(nr, f) == Piece.EMPTY) {
            addPawnMove(r, f, nr, f, promoRank, moves)
            // Double push
            if (r == startRank && board.get(r + 2*dir, f) == Piece.EMPTY)
                moves.add(EngineMove(r, f, r + 2*dir, f))
        }
        // Captures
        for (df in intArrayOf(-1, 1)) {
            val nf = f + df
            if (nf !in 0..7 || nr !in 0..7) continue
            val target = board.get(nr, nf)
            val isEnemy = if (white) target < 0 else target > 0
            val isEP = board.enPassant?.let { it.rank == nr && it.file == nf } == true
            if (isEnemy || isEP) addPawnMove(r, f, nr, nf, promoRank, moves)
        }
    }

    private fun addPawnMove(fr: Int, ff: Int, tr: Int, tf: Int, promoRank: Int, moves: MutableList<EngineMove>) {
        if (tr == promoRank)
            for (promo in intArrayOf(Piece.QUEEN, Piece.ROOK, Piece.BISHOP, Piece.KNIGHT))
                moves.add(EngineMove(fr, ff, tr, tf, promo))
        else
            moves.add(EngineMove(fr, ff, tr, tf))
    }

    private fun genLeaperMoves(board: ChessBoard, r: Int, f: Int, deltas: Array<IntArray>, moves: MutableList<EngineMove>) {
        val white = board.whiteToMove
        for ((dr, df) in deltas) {
            val nr = r + dr; val nf = f + df
            if (nr !in 0..7 || nf !in 0..7) continue
            val t = board.get(nr, nf)
            if (t == Piece.EMPTY || (if (white) t < 0 else t > 0)) moves.add(EngineMove(r, f, nr, nf))
        }
    }

    private fun genSlidingMoves(board: ChessBoard, r: Int, f: Int, dirs: Array<IntArray>, moves: MutableList<EngineMove>) {
        val white = board.whiteToMove
        for ((dr, df) in dirs) {
            var nr = r + dr; var nf = f + df
            while (nr in 0..7 && nf in 0..7) {
                val t = board.get(nr, nf)
                if (t == Piece.EMPTY) { moves.add(EngineMove(r, f, nr, nf)); nr += dr; nf += df }
                else { if (if (white) t < 0 else t > 0) moves.add(EngineMove(r, f, nr, nf)); break }
            }
        }
    }

    private fun genKingMoves(board: ChessBoard, r: Int, f: Int, moves: MutableList<EngineMove>) {
        genLeaperMoves(board, r, f, KING_DELTAS, moves)
        // Castling
        val white = board.whiteToMove
        if (white && 'K' in board.castling && board.get(0,5) == Piece.EMPTY && board.get(0,6) == Piece.EMPTY)
            moves.add(EngineMove(0, 4, 0, 6))
        if (white && 'Q' in board.castling && board.get(0,1) == Piece.EMPTY && board.get(0,2) == Piece.EMPTY && board.get(0,3) == Piece.EMPTY)
            moves.add(EngineMove(0, 4, 0, 2))
        if (!white && 'k' in board.castling && board.get(7,5) == Piece.EMPTY && board.get(7,6) == Piece.EMPTY)
            moves.add(EngineMove(7, 4, 7, 6))
        if (!white && 'q' in board.castling && board.get(7,1) == Piece.EMPTY && board.get(7,2) == Piece.EMPTY && board.get(7,3) == Piece.EMPTY)
            moves.add(EngineMove(7, 4, 7, 2))
    }

    private fun inCheck(board: ChessBoard, whiteKing: Boolean): Boolean {
        // Find king
        var kr = -1; var kf = -1
        for (r in 0..7) for (f in 0..7) {
            val p = board.get(r, f)
            if ((whiteKing && p == Piece.KING) || (!whiteKing && p == -Piece.KING)) { kr = r; kf = f }
        }
        if (kr == -1) return true
        // Check by pawns
        val pawnDir = if (whiteKing) 1 else -1
        for (df in intArrayOf(-1, 1)) {
            val nr = kr + pawnDir; val nf = kf + df
            if (nr in 0..7 && nf in 0..7) {
                val p = board.get(nr, nf)
                if ((whiteKing && p == -Piece.PAWN) || (!whiteKing && p == Piece.PAWN)) return true
            }
        }
        // Check by knights
        for ((dr, df) in KNIGHT_DELTAS) {
            val nr = kr+dr; val nf = kf+df
            if (nr in 0..7 && nf in 0..7) {
                val p = board.get(nr, nf)
                if ((whiteKing && p == -Piece.KNIGHT) || (!whiteKing && p == Piece.KNIGHT)) return true
            }
        }
        // Check by sliders
        for ((dr, df) in ROOK_DIRS) {
            var nr = kr+dr; var nf = kf+df
            while (nr in 0..7 && nf in 0..7) {
                val p = board.get(nr, nf)
                if (p != Piece.EMPTY) {
                    val enemy = if (whiteKing) p < 0 else p > 0
                    if (enemy && (abs(p) == Piece.ROOK || abs(p) == Piece.QUEEN)) return true
                    break
                }
                nr += dr; nf += df
            }
        }
        for ((dr, df) in BISHOP_DIRS) {
            var nr = kr+dr; var nf = kf+df
            while (nr in 0..7 && nf in 0..7) {
                val p = board.get(nr, nf)
                if (p != Piece.EMPTY) {
                    val enemy = if (whiteKing) p < 0 else p > 0
                    if (enemy && (abs(p) == Piece.BISHOP || abs(p) == Piece.QUEEN)) return true
                    break
                }
                nr += dr; nf += df
            }
        }
        // Check by king
        for ((dr, df) in KING_DELTAS) {
            val nr = kr+dr; val nf = kf+df
            if (nr in 0..7 && nf in 0..7) {
                val p = board.get(nr, nf)
                if ((whiteKing && p == -Piece.KING) || (!whiteKing && p == Piece.KING)) return true
            }
        }
        return false
    }

    // ── Board cloning / restoration ───────────────────────────────────────────

    private data class BoardSnapshot(
        val board: Array<IntArray>,
        val whiteToMove: Boolean,
        val castling: String,
        val enPassant: Square?,
        val halfMoveClock: Int,
        val fullMoveNumber: Int
    )

    private fun cloneBoard(b: ChessBoard) = BoardSnapshot(
        Array(8) { b.get(it, 0).let { _ -> IntArray(8) { f -> b.get(it, f) } } },
        b.whiteToMove, b.castling, b.enPassant, b.halfMoveClock, b.fullMoveNumber
    )

    private fun restoreBoard(b: ChessBoard, s: BoardSnapshot) {
        for (r in 0..7) for (f in 0..7) b.setInternal(r, f, s.board[r][f])
        b.whiteToMove = s.whiteToMove
        b.castling = s.castling
        b.enPassant = s.enPassant
        b.halfMoveClock = s.halfMoveClock
        b.fullMoveNumber = s.fullMoveNumber
    }

    private fun applyMove(board: ChessBoard, move: EngineMove) {
        val uci = move.toUci()
        board.applyUci(uci)
    }

    companion object {
        private val KNIGHT_DELTAS = arrayOf(intArrayOf(-2,-1),intArrayOf(-2,1),intArrayOf(-1,-2),intArrayOf(-1,2),
            intArrayOf(1,-2),intArrayOf(1,2),intArrayOf(2,-1),intArrayOf(2,1))
        private val BISHOP_DIRS  = arrayOf(intArrayOf(-1,-1),intArrayOf(-1,1),intArrayOf(1,-1),intArrayOf(1,1))
        private val ROOK_DIRS    = arrayOf(intArrayOf(-1,0),intArrayOf(1,0),intArrayOf(0,-1),intArrayOf(0,1))
        private val QUEEN_DIRS   = BISHOP_DIRS + ROOK_DIRS
        private val KING_DELTAS  = QUEEN_DIRS + arrayOf(intArrayOf(-1,-1),intArrayOf(-1,1),intArrayOf(1,-1),intArrayOf(1,1),
            intArrayOf(-1,0),intArrayOf(1,0),intArrayOf(0,-1),intArrayOf(0,1))
    }
}
