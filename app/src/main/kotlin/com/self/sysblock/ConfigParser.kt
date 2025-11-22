package com.self.sysblock

import android.util.Log

object ConfigParser {

    data class AppRule(
        val packageName: String,
        val limitMinutes: Int, 
        val strictMode: Boolean
    )

    data class SystemConfig(
        val rules: List<AppRule> = emptyList(),
        val masterSwitch: Boolean = true
    )

    fun parse(rawText: String): SystemConfig {
        val rules = mutableListOf<AppRule>()
        var masterSwitch = true

        val lines = rawText.lines()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

            try {
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
        return SystemConfig(rules, masterSwitch)
    }

    fun getDefaultConfig(): String {
        return """
            # SysBlock Config
            # Format: Package | 0 | StrictMode
            
            SET | MASTER_SWITCH | true
            
            # Example (Uncomment to test):
            # com.android.chrome | 0 | true
        """.trimIndent()
    }
}