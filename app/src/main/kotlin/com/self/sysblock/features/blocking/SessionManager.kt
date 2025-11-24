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
            
            // 1. Re-verify rule exists
            val rule = config.rules.find { it.packageName == pkg }
            if (rule == null) {
                stopMonitoring()
                return
            }

            // 2. Calculate Remaining Time
            val prefs = context.getSharedPreferences("SysBlockSessions", Context.MODE_PRIVATE)
            val expiryTime = prefs.getLong(pkg, 0L)
            val now = System.currentTimeMillis()
            val remainingMs = expiryTime - now
            
            // 3. Check Limits
            val isSessionValid = remainingMs > 0
            val isLockedOut = PenaltyManager.isLockedOut(context, pkg)
            val currentUsage = UsageManager.getDailyUsage(context, pkg)
            val isOverLimit = rule.limitMinutes > 0 && currentUsage >= rule.limitMinutes

            // 4. BLOCKING TRIGGER
            if (!isSessionValid || isLockedOut || isOverLimit) {
                OverlayController.hide()
                launchBlockScreen(pkg)
                // Do NOT clear currentMonitoredPackage here immediately to avoid loop race conditions
                // Instead, stopMonitoring() will be called by the service when window changes
                // or we can just stop the loop here.
                handler.removeCallbacks(this)
                currentMonitoredPackage = null
                return
            }
            
            // 5. OVERLAY TRIGGER
            // Only show if we are "actively" in the session and time is low
            if (remainingMs <= WARNING_THRESHOLD_MS) {
                OverlayController.showWarning(context, remainingMs, WARNING_THRESHOLD_MS)
            } else {
                OverlayController.hide()
            }
            
            // 6. LOOP
            handler.postDelayed(this, 200)
        }
    }

    fun startMonitoring(pkg: String) {
        // If we are already monitoring this package, do nothing.
        // This prevents the loop from being reset constantly by scroll events.
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
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        intent.putExtra("pkg_name", pkgName)
        context.startActivity(intent)
    }
}