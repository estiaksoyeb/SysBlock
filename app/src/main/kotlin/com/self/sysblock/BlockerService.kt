package com.self.sysblock

import android.accessibilityservice.AccessibilityService
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import java.util.Calendar

class BlockerService : AccessibilityService() {

    private var cachedConfig: ConfigParser.SystemConfig? = null
    private val handler = Handler(Looper.getMainLooper())
    private var currentMonitoredPackage: String? = null

    // Watchdog (Combined Logic)
    private val sessionCheckerRunnable = object : Runnable {
        override fun run() {
            val pkg = currentMonitoredPackage ?: return
            val config = cachedConfig ?: return
            val rule = config.rules.find { it.packageName == pkg }

            val isSessionValid = isSessionActive(pkg)
            val isLockedOut = PenaltyManager.isLockedOut(applicationContext, pkg)
            
            val currentUsage = getDailyUsage(pkg)
            val isOverLimit = rule != null && currentUsage >= rule.limitMinutes

            if (!isSessionValid || isLockedOut || isOverLimit) {
                launchBlockScreen(pkg)
                currentMonitoredPackage = null
                return
            }
            
            handler.postDelayed(this, 1000)
        }
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        if (key == "raw_config") updateConfigCache(sharedPreferences)
    }

    private val IGNORED_PACKAGES = setOf(
        "com.android.systemui",
        "android",
        "com.google.android.inputmethod.latin",
        "com.self.sysblock",
        "com.android.launcher",
        "com.google.android.apps.nexuslauncher",
        "com.miui.home",
        "com.sec.android.app.launcher"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        val prefs = getSharedPreferences("SysBlockPrefs", Context.MODE_PRIVATE)
        updateConfigCache(prefs)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    private fun updateConfigCache(prefs: SharedPreferences) {
        val rawConfig = prefs.getString("raw_config", ConfigParser.getDefaultConfig()) ?: ""
        cachedConfig = ConfigParser.parse(rawConfig)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkgName = event.packageName?.toString() ?: return
            
            // --- SECURITY WATCHDOG (UPDATED) ---
            val config = cachedConfig
            if (config != null && config.preventUninstall && config.masterSwitch) {
                if (pkgName == "com.android.settings") {
                    val className = event.className?.toString() ?: ""
                    // event.text is a list; toString() usually looks like "[Accessibility]"
                    val eventText = event.text.toString() 
                    
                    var shouldBlock = false

                    // 1. Block Accessibility (Matches Class OR Title)
                    if (className.contains("Accessibility", ignoreCase = true) || 
                        eventText.contains("Accessibility", ignoreCase = true)) {
                        shouldBlock = true
                    }
                    
                    // 2. Block Device Admin
                    if (className.contains("DeviceAdmin", ignoreCase = true) || 
                        eventText.contains("Device Admin", ignoreCase = true)) {
                        shouldBlock = true
                    }

                    // 3. Block Usage Access (NEW)
                    // Matches "Usage Access", "Special app access", "Usage data access" (Samsung)
                    if (className.contains("UsageAccess", ignoreCase = true) ||
                        eventText.contains("Usage Access", ignoreCase = true) ||
                        eventText.contains("Usage Data", ignoreCase = true)) {
                         shouldBlock = true
                    }

                    // 4. THE CATCH-ALL: Block "SysBlock" inside Settings
                    // This blocks: App Info, SysBlock Accessibility Page, SysBlock Usage Page
                    // regardless of how you got there.
                    if (eventText.contains("SysBlock", ignoreCase = true) || 
                        eventText.contains("com.self.sysblock", ignoreCase = true)) {
                        shouldBlock = true
                    }

                    if (shouldBlock) {
                        performGlobalAction(GLOBAL_ACTION_HOME)
                        return 
                    }
                }
            }
            // ------------------------------------------

            stopMonitoring()

            if (IGNORED_PACKAGES.contains(pkgName)) return
            if (config == null || !config.masterSwitch) return

            val rule = config.rules.find { it.packageName == pkgName } ?: return

            if (rule.strictMode) {
                if (PenaltyManager.isLockedOut(this, pkgName)) {
                    launchBlockScreen(pkgName)
                    return
                }

                if (isSessionActive(pkgName)) {
                    currentMonitoredPackage = pkgName
                    handler.post(sessionCheckerRunnable)
                } else {
                    launchBlockScreen(pkgName)
                }
            }
        }
    }

    private fun getDailyUsage(pkgName: String): Int {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startTime = calendar.timeInMillis

        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        val usage = stats?.find { it.packageName == pkgName }
        return ((usage?.totalTimeInForeground ?: 0) / 1000 / 60).toInt()
    }

    private fun stopMonitoring() {
        handler.removeCallbacks(sessionCheckerRunnable)
        currentMonitoredPackage = null
    }

    private fun isSessionActive(pkg: String): Boolean {
        val prefs = getSharedPreferences("SysBlockSessions", Context.MODE_PRIVATE)
        val expiryTime = prefs.getLong(pkg, 0L)
        return System.currentTimeMillis() < expiryTime
    }

    private fun launchBlockScreen(pkgName: String) {
        val intent = Intent(this, BlockingActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.putExtra("pkg_name", pkgName)
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMonitoring()
        val prefs = getSharedPreferences("SysBlockPrefs", Context.MODE_PRIVATE)
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onInterrupt() {}
}
