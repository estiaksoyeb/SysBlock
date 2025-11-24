package com.self.sysblock.features.watchdog

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.self.sysblock.data.config.SystemConfig

object SecurityWatchdog {

    fun checkAndBlock(
        service: AccessibilityService,
        event: AccessibilityEvent,
        config: SystemConfig
    ): Boolean {
        if (!config.preventUninstall || !config.masterSwitch) return false

        val pkgName = event.packageName?.toString() ?: return false
        
        if (pkgName == "com.android.settings" || 
            pkgName.contains("packageinstaller") || 
            pkgName.contains("permissioncontroller")) {
            
            val eventText = event.text.toString().lowercase()
            val className = event.className?.toString()?.lowercase() ?: ""
            
            var shouldBlock = false

            // 1. HARD BLOCK: Usage Access
            if (eventText.contains("usage access") || className.contains("usageaccess")) {
                shouldBlock = true
            }

            // 2. SMART BLOCK: Accessibility & Device Admin & App Info
            if (eventText.contains("sysblock") || eventText.contains("com.self.sysblock")) {
                
                var isSafe = false
                if (className.contains("accessibilitysettings")) isSafe = true
                if (className.contains("deviceadminsettings")) isSafe = true

                if (!isSafe) shouldBlock = true
            }

            if (shouldBlock) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                Toast.makeText(service.applicationContext, "Disable PREVENT_UNINSTALL first", Toast.LENGTH_SHORT).show()
                return true
            }
        }
        return false
    }
}