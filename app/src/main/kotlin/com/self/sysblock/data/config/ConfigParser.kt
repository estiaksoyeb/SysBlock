package com.self.sysblock.data.config

import android.util.Log

object ConfigParser {

    fun parse(rawText: String): SystemConfig {
        val rules = mutableListOf<AppRule>()
        var masterSwitch = true
        var preventUninstall = false // Default is false

        val lines = rawText.lines()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

            try {
                // Check for the new keyword
                if (trimmed == "PREVENT_UNINSTALL") {
                    // SAFETY HATCH: Commented out to regain control
                    // preventUninstall = true 
                    continue
                }

                val parts = trimmed.split("|").map { it.trim() }

                if (parts[0] == "SET") {
                    if (parts.size >= 3 && parts[1] == "MASTER_SWITCH") {
                        masterSwitch = parts[2].toBoolean()
                    }
                } else {
                    // Format: com.package | 0 | true
                    if (parts.size >= 3) {
                        val pkg = parts[0]
                        val limit = parts[1].toIntOrNull() ?: 0
                        val strict = parts[2].toBoolean()
                        rules.add(AppRule(pkg, limit, strict))
                    }
                }
            } catch (e: Exception) {
                Log.e("SysBlock", "Parser Error: $trimmed")
            }
        }
        // Return the config with the new value
        return SystemConfig(rules, masterSwitch, preventUninstall)
    }

    fun getDefaultConfig(): String {
        return """
            # SysBlock Config
            # Add line below to lock uninstall:
            # PREVENT_UNINSTALL
            
            SET | MASTER_SWITCH | true
            
            # Example:
            # com.android.chrome | 0 | true
        """.trimIndent()
    }
}