package com.self.sysblock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

// Simple Navigation State
enum class Screen { HOME, EDITOR }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            // Very basic navigation without using Navigation Component overhead
            val currentScreen = androidx.compose.runtime.remember { 
                androidx.compose.runtime.mutableStateOf(Screen.HOME) 
            }

            when (currentScreen.value) {
                Screen.HOME -> HomeScreen(
                    onNavigateToEditor = { currentScreen.value = Screen.EDITOR }
                )
                Screen.EDITOR -> EditorScreen(
                    onBack = { currentScreen.value = Screen.HOME }
                )
            }
        }
    }
}