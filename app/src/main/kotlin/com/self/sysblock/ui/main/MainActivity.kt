package com.self.sysblock.ui.main

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import com.self.sysblock.ui.editor.EditorScreen
import com.self.sysblock.ui.home.HomeScreen

enum class Screen { HOME, EDITOR }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Base Setup (Force White Icons globally first)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false 
        insetsController.isAppearanceLightNavigationBars = false 

        setContent {
            val currentScreen = remember { mutableStateOf(Screen.HOME) }

            // 2. DYNAMIC COLOR LOGIC
            // We decide the color based on which screen is active
            val systemBarColor = when (currentScreen.value) {
                Screen.HOME -> Color(0xFF121212)   // Dark Grey
                Screen.EDITOR -> Color(0xFF1E1E1E) // Slightly Lighter Grey
            }

            // 3. APPLY COLOR INSTANTLY
            // Whenever 'systemBarColor' changes, this code runs and updates the window
            LaunchedEffect(systemBarColor) {
                val colorInt = systemBarColor.toArgb()
                window.statusBarColor = colorInt
                window.navigationBarColor = colorInt
                window.setBackgroundDrawable(ColorDrawable(colorInt))
            }

            // 4. RENDER SCREEN
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = systemBarColor, // Sync Compose background with Window background
                    surface = systemBarColor,
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
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
}