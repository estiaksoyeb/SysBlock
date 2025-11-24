package com.self.sysblock.features.blocking

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.self.sysblock.data.config.SystemConfig
import com.self.sysblock.features.overlay.OverlayController
import com.self.sysblock.features.penalty.PenaltyManager
import com.self.sysblock.features.usage.UsageManager
import com.self.sysblock.ui.blocking.BlockingActivity

class SessionManager(
    private val context: Context,
    private val configProvider: () -> SystemConfig?
) {
    private val handler = Handler(Looper.getMainLooper())
    private var currentMonitoredPackage: String? = null
    private val WARNING_THRESHOLD_MS = 15_000L

    private val sessionCheckerRunnable = object : Runnable {
        override fun run() {
            val pkg = currentMonitoredPackage ?: return
            val config = configProvider() ?: return
            val rule = config.rules.find { it.packageName == pkg }

            // 1. Calculate Remaining Time
            val prefs = context.getSharedPreferences("SysBlockSessions", Context.MODE_PRIVATE)
            val expiryTime = prefs.getLong(pkg, 0L)
            val now = System.currentTimeMillis()
            val remainingMs = expiryTime - now
            
            val isSessionValid = remainingMs > 0
            val isLockedOut = PenaltyManager.isLockedOut(context, pkg)
            
            val currentUsage = UsageManager.getDailyUsage(context, pkg)
            val isOverLimit = rule != null && currentUsage >= rule.limitMinutes

            // 2. Blocking Check
            if (!isSessionValid || isLockedOut || isOverLimit) {
                OverlayController.hide() // Clean up UI
                launchBlockScreen(pkg)
                currentMonitoredPackage = null
                return
            }
            
            // 3. Overlay Check (15s Warning)
            if (remainingMs <= WARNING_THRESHOLD_MS) {
                OverlayController.showWarning(context, remainingMs, WARNING_THRESHOLD_MS)
            } else {
                OverlayController.hide()
            }
            
            // Re-run fast for smooth animation
            handler.postDelayed(this, 200)
        }
    }

    fun startMonitoring(pkg: String) {
        if (currentMonitoredPackage == pkg) return
        
        currentMonitoredPackage = pkg
        handler.removeCallbacks(sessionCheckerRunnable)
        handler.post(sessionCheckerRunnable)
    }

    fun stopMonitoring() {
        OverlayController.hide()
        handler.removeCallbacks(sessionCheckerRunnable)
        currentMonitoredPackage = null
    }

    fun launchBlockScreen(pkgName: String) {
        val intent = Intent(context, BlockingActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.putExtra("pkg_name", pkgName)
        context.startActivity(intent)
    }
}