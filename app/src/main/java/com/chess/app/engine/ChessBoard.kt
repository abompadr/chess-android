package com.chess.app.engine

// Piece constants: positive = white, negative = black, 0 = empty
object Piece {
    const val EMPTY = 0
    const val PAWN = 1
    const val KNIGHT = 2
    const val BISHOP = 3
    const val ROOK = 4
    const val QUEEN = 5
    const val KING = 6
}

data class Square(val file: Int, val rank: Int) {  // file 0=a..7=h, rank 0=1..7=8
    override fun toString() = "${"abcdefgh"[file]}${rank + 1}"
    companion object {
        fun fromAlg(s: String) = Square("abcdefgh".indexOf(s[0]), s[1].digitToInt() - 1)
    }
}

data class Move(val from: Square, val to: Square, val promotion: Int = Piece.EMPTY) {
    fun toUci(): String {
        val promo = if (promotion != Piece.EMPTY) "pnbrqk"[promotion] else ""
        return "${from}${to}$promo"
    }
    companion object {
        fun fromUci(uci: String): Move {
            val from = Square.fromAlg(uci.substring(0, 2))
            val to   = Square.fromAlg(uci.substring(2, 4))
            val promo = if (uci.length == 5) "pnbrqk".indexOf(uci[4]) else Piece.EMPTY
            return Move(from, to, promo)
        }
    }
}

class ChessBoard {
    // board[rank][file], positive=white, negative=black
    private val board = Array(8) { IntArray(8) }
    var whiteToMove = true
    var castling = "KQkq"
    var enPassant: Square? = null
    var halfMoveClock = 0
    var fullMoveNumber = 1
    val moveHistory = mutableListOf<String>()  // UCI strings

    init { reset() }

    fun reset() {
        val backRank = intArrayOf(Piece.ROOK, Piece.KNIGHT, Piece.BISHOP, Piece.QUEEN,
                                   Piece.KING, Piece.BISHOP, Piece.KNIGHT, Piece.ROOK)
        for (f in 0..7) {
            board[0][f] =  backRank[f]
            board[1][f] =  Piece.PAWN
            board[6][f] = -Piece.PAWN
            board[7][f] = -backRank[f]
        }
        for (r in 2..5) board[r].fill(Piece.EMPTY)
        whiteToMove = true
        castling = "KQkq"
        enPassant = null
        halfMoveClock = 0
        fullMoveNumber = 1
        moveHistory.clear()
    }

    fun get(sq: Square) = board[sq.rank][sq.file]
    fun get(rank: Int, file: Int) = board[rank][file]

    fun applyUci(uci: String) {
        val move = Move.fromUci(uci)
        val piece = board[move.from.rank][move.from.file]
        val absPiece = Math.abs(piece)

        // En passant capture
        if (absPiece == Piece.PAWN && move.to == enPassant) {
            val capRank = if (whiteToMove) move.to.rank - 1 else move.to.rank + 1
            board[capRank][move.to.file] = Piece.EMPTY
        }

        // Castling: move rook
        if (absPiece == Piece.KING && Math.abs(move.from.file - move.to.file) == 2) {
            val rank = move.from.rank
            if (move.to.file == 6) { board[rank][5] = board[rank][7]; board[rank][7] = Piece.EMPTY }
            else                   { board[rank][3] = board[rank][0]; board[rank][0] = Piece.EMPTY }
        }

        // Move piece
        board[move.to.rank][move.to.file] = if (move.promotion != Piece.EMPTY)
            (if (whiteToMove) 1 else -1) * move.promotion else piece
        board[move.from.rank][move.from.file] = Piece.EMPTY

        // Update en passant
        enPassant = if (absPiece == Piece.PAWN && Math.abs(move.from.rank - move.to.rank) == 2)
            Square(move.from.file, (move.from.rank + move.to.rank) / 2) else null

        // Update castling rights
        if (absPiece == Piece.KING) castling = castling.replace(if (whiteToMove) Regex("[KQ]") else Regex("[kq]"), "")
        if (move.from == Square(0,0) || move.to == Square(0,0)) castling = castling.replace("Q","")
        if (move.from == Square(0,7) || move.to == Square(0,7)) castling = castling.replace("K","")
        if (move.from == Square(7,0) || move.to == Square(7,0)) castling = castling.replace("q","")
        if (move.from == Square(7,7) || move.to == Square(7,7)) castling = castling.replace("k","")

        halfMoveClock = if (absPiece == Piece.PAWN || board[move.to.rank][move.to.file] != Piece.EMPTY) 0 else halfMoveClock + 1
        if (!whiteToMove) fullMoveNumber++
        whiteToMove = !whiteToMove
        moveHistory.add(uci)
    }

    fun toFen(): String {
        val sb = StringBuilder()
        for (r in 7 downTo 0) {
            var empty = 0
            for (f in 0..7) {
                val p = board[r][f]
                if (p == Piece.EMPTY) { empty++ }
                else {
                    if (empty > 0) { sb.append(empty); empty = 0 }
                    val ch = "pnbrqk"[Math.abs(p) - 1]
                    sb.append(if (p > 0) ch.uppercaseChar() else ch)
                }
            }
            if (empty > 0) sb.append(empty)
            if (r > 0) sb.append('/')
        }
        sb.append(' ')
        sb.append(if (whiteToMove) 'w' else 'b')
        sb.append(' ')
        sb.append(if (castling.isEmpty()) "-" else castling)
        sb.append(' ')
        sb.append(enPassant?.toString() ?: "-")
        sb.append(' ')
        sb.append(halfMoveClock)
        sb.append(' ')
        sb.append(fullMoveNumber)
        return sb.toString()
    }

    companion object {
        const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    }
}
