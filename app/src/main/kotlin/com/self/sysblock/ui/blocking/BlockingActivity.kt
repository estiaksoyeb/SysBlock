package com.self.sysblock.ui.blocking

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat

class BlockingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // --- 1. PROGRAMMATIC STYLING (TypeAssist Pattern) ---
        // We define the color (Pitch Black for blocking)
        val blockingColor = Color.Black.toArgb()
        
        // Apply to Status Bar & Nav Bar
        window.statusBarColor = blockingColor
        window.navigationBarColor = blockingColor
        
        // Apply to Window Background (Prevents white flash on startup/rotate)
        window.setBackgroundDrawable(ColorDrawable(blockingColor))
        
        // Force System Icons to be White (Light Mode = false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false
        // ----------------------------------------------------

        // Fix for deprecated overridePendingTransition
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
        
        val blockedPkg = intent.getStringExtra("pkg_name") ?: "App"
        setContent { BlockingSessionScreen(blockedPkg) }
    }
}