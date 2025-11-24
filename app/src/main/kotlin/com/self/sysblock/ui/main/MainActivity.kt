package com.self.sysblock.ui.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.self.sysblock.ui.editor.EditorScreen
import com.self.sysblock.ui.home.HomeScreen

// Simple Navigation State
enum class Screen { HOME, EDITOR }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            // Very basic navigation without using Navigation Component overhead
            val currentScreen = remember { 
                mutableStateOf(Screen.HOME) 
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