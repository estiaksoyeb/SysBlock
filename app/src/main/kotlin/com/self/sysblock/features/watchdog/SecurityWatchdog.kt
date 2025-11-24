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
        
        // Monitor Settings, Package Installer, and Permission Controller
        if (pkgName == "com.android.settings" || 
            pkgName.contains("packageinstaller") || 
            pkgName.contains("permissioncontroller")) {
            
            val eventText = event.text.toString().lowercase()
            val className = event.className?.toString()?.lowercase() ?: ""
            
            var shouldBlock = false

            // --- 1. HARD BLOCK: USAGE ACCESS ---
            // Blocks entry to the Usage Access list entirely.
            // TO DISABLE: Comment out the if-block below.
            if (eventText.contains("usage access") || className.contains("usageaccess")) {
                shouldBlock = true
            }

            // --- 2. HARD BLOCK: DEVICE ADMIN ---
            // Prevents entering Device Admin menu to stop rapid-click bypass.
            if (className.contains("deviceadmin")) {
                shouldBlock = true
            }

            /* --- PREVIOUS DEVICE ADMIN SOFT MODE (COMMENTED OUT) ---
             * If you want to allow browsing the admin list and only block activation:
             * if (className.contains("deviceadminadd")) {
             * shouldBlock = true
             * }
             */

            // --- 3. SMART BLOCK (Accessibility, App Info) ---
            // Logic: If "SysBlock" appears on screen...
            if (eventText.contains("sysblock") || eventText.contains("com.self.sysblock")) {
                
                var isSafe = false
                
                // SAFE LISTS: Allowed to browse these menus
                if (className.contains("accessibilitysettings")) isSafe = true
                if (className.contains("manageapplications")) isSafe = true // App List
                
                // Note: Usage Access and Device Admin are now handled by HARD BLOCK above,
                // so we don't need to safe-list them here.

                // If not in a safe list, block it.
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