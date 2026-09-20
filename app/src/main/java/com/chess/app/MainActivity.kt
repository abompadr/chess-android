package com.chess.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.chess.app.data.Profile
import com.chess.app.ui.GameScreen
import com.chess.app.ui.ProfileScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var activeProfile by remember { mutableStateOf<Profile?>(null) }

            if (activeProfile == null) {
                ProfileScreen(onPlay = { activeProfile = it })
            } else {
                GameScreen(
                    profile = activeProfile!!,
                    onBack = { activeProfile = null }
                )
            }
        }
    }
}
