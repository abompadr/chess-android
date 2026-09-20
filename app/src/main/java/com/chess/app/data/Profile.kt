package com.chess.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val skillLevel: Int = 10,   // Stockfish Skill Level 0-20
    val timeControlMinutes: Int = 10  // 0 = unlimited
)
