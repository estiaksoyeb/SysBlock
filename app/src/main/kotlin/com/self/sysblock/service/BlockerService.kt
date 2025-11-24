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
        val pkgName = event.packageName?.toString() ?: return
        val eventType = event.eventType

        // --- 1. SECURITY WATCHDOG ---
        // We check Watchdog on WindowStateChanged (Standard) AND ContentChanged (Fixes laggy titles)
        // We only check ContentChanged for Settings/Installer to save battery.
        val isSettingsEvent = pkgName == "com.android.settings" || 
                              pkgName.contains("packageinstaller") || 
                              pkgName.contains("permissioncontroller")

        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || 
           (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && isSettingsEvent)) {
            
            val config = cachedConfig
            if (config != null) {
                if (SecurityWatchdog.checkAndBlock(this, event, config)) {
                    return
                }
            }
        }

        // --- 2. MAIN BLOCKING LOGIC ---
        // Only run main blocking logic on WindowStateChanged (New app opened)
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            
            // Stop monitoring previous app
            sessionManager.stopMonitoring()

            if (IGNORED_PACKAGES.contains(pkgName)) return
            val config = cachedConfig
            if (config == null || !config.masterSwitch) return

            val rule = config.rules.find { it.packageName == pkgName } ?: return

            if (rule.strictMode) {
                if (PenaltyManager.isLockedOut(this, pkgName)) {
                    sessionManager.launchBlockScreen(pkgName)
                    return
                }

                if (isSessionActive(pkgName)) {
                    sessionManager.startMonitoring(pkgName)
                } else {
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