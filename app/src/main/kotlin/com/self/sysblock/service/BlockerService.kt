package com.self.sysblock.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.view.accessibility.AccessibilityEvent
import com.self.sysblock.data.config.ConfigParser
import com.self.sysblock.data.config.SystemConfig
import com.self.sysblock.features.blocking.SessionManager
import com.self.sysblock.features.overlay.OverlayController
import com.self.sysblock.features.penalty.PenaltyManager
import com.self.sysblock.features.watchdog.SecurityWatchdog

class BlockerService : AccessibilityService() {

    private var cachedConfig: SystemConfig? = null
    private lateinit var sessionManager: SessionManager
    
    // Track current package to avoid redundant calls
    private var lastPkgName: String? = null

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        if (key == "raw_config") updateConfigCache(sharedPreferences)
    }

    // IGNORE LIST: These packages should NOT trigger a "Stop Monitoring" event
    // If we are in Facebook and the Keyboard opens, we are STILL in Facebook.
    private val SYSTEM_PACKAGES = setOf(
        "com.android.systemui",
        "android",
        "com.google.android.inputmethod.latin", // Gboard
        "com.samsung.android.honeyboard",      // Samsung Keyboard
        "com.self.sysblock"                    // Our own app
    )

    private val LAUNCHER_PACKAGES = setOf(
        "com.android.launcher",
        "com.google.android.apps.nexuslauncher",
        "com.miui.home",
        "com.sec.android.app.launcher",
        "com.huawei.android.launcher"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        
        OverlayController.init(this)
        sessionManager = SessionManager(this.applicationContext) { cachedConfig }

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
            
            // Optimization: Don't re-process if we are still in the same package
            if (pkgName == lastPkgName) return
            lastPkgName = pkgName

            // --- 1. SECURITY WATCHDOG ---
            val config = cachedConfig
            if (config != null) {
                if (SecurityWatchdog.checkAndBlock(this, event, config)) {
                    return
                }
            }

            // --- 2. CONTEXT SWITCH LOGIC ---
            
            // If we switched to a SYSTEM package (Keyboard, Notification Shade), 
            // DO NOT stop monitoring the previous app. We assume the previous app is still active underneath.
            if (SYSTEM_PACKAGES.contains(pkgName)) {
                return 
            }

            // If we switched to a Launcher or any other app, STOP monitoring the previous one.
            // This ensures the overlay disappears when you go Home.
            if (LAUNCHER_PACKAGES.contains(pkgName) || (config?.rules?.none { it.packageName == pkgName } == true)) {
                sessionManager.stopMonitoring()
            }

            // --- 3. APP BLOCKING LOGIC ---
            if (config == null || !config.masterSwitch) return

            val rule = config.rules.find { it.packageName == pkgName } ?: return

            if (rule.strictMode) {
                if (PenaltyManager.isLockedOut(this, pkgName)) {
                    sessionManager.launchBlockScreen(pkgName)
                    return
                }

                if (isSessionActive(pkgName)) {
                    // We are in a tracked app -> Start/Resume Loop
                    sessionManager.startMonitoring(pkgName)
                } else {
                    // Time expired or never started -> Block
                    sessionManager.launchBlockScreen(pkgName)
                }
            }
        }
    }

    private fun isSessionActive(pkg: String): Boolean {
        val prefs = getSharedPreferences("SysBlockSessions", Context.MODE_PRIVATE)
        val expiryTime = prefs.getLong(pkg, 0L)
        return System.currentTimeMillis() < expiryTime
    }

    override fun onDestroy() {
        super.onDestroy()
        sessionManager.stopMonitoring()
        val prefs = getSharedPreferences("SysBlockPrefs", Context.MODE_PRIVATE)
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onInterrupt() {}
}