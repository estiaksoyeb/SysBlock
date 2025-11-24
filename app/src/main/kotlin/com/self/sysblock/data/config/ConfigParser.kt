package com.self.sysblock.data.config

import android.util.Log

object ConfigParser {

    fun parse(rawText: String): SystemConfig {
        val rules = mutableListOf<AppRule>()
        var masterSwitch = true
        var preventUninstall = false 
        var sessionTimes = mutableListOf<Int>() 

        val lines = rawText.lines()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

            try {
                if (trimmed == "PREVENT_UNINSTALL") {
                    // SAFETY HATCH: Commented out to regain control
                    preventUninstall = true 
                    continue
                }

                val parts = trimmed.split("|").map { it.trim() }

                if (parts[0] == "SET") {
                    val key = parts.getOrNull(1) ?: ""
                    
                    when (key) {
                        "MASTER_SWITCH" -> {
                            if (parts.size >= 3) masterSwitch = parts[2].toBoolean()
                        }
                        "SESSION_TIME" -> {
                            // SET | SESSION_TIME | 5 | 10 | 20
                            val timesInMinutes = parts.drop(2).mapNotNull { it.toIntOrNull() }
                            if (timesInMinutes.isNotEmpty()) {
                                sessionTimes.clear()
                                sessionTimes.addAll(timesInMinutes.map { it * 60 })
                            }
                        }
                        "APPLOCK" -> {
                            // NEW FORMAT: SET | APPLOCK | com.package | [TIME]
                            // Example: SET | APPLOCK | com.facebook.katana | 30m
                            if (parts.size >= 4) {
                                val pkg = parts[2]
                                val timeStr = parts[3]
                                val minutes = parseDuration(timeStr)
                                // APPLOCK implies strict blocking
                                rules.add(AppRule(pkg, minutes, strictMode = true))
                            }
                        }
                    }
                } else {
                    // Legacy Format: com.package | 0 | true
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
        
        return if (sessionTimes.isNotEmpty()) {
            SystemConfig(rules, masterSwitch, preventUninstall, sessionTimes)
        } else {
            SystemConfig(rules, masterSwitch, preventUninstall)
        }
    }

    // Helper to parse "1h", "30m", "90" into minutes
    private fun parseDuration(input: String): Int {
        // 1. If it's just a number (e.g. "30"), return it
        input.toIntOrNull()?.let { return it }

        // 2. Parse suffixes (e.g. "1h 30m")
        var totalMinutes = 0
        var currentNumber = ""
        
        for (char in input.lowercase()) {
            if (char.isDigit()) {
                currentNumber += char
            } else if (char == 'h') {
                totalMinutes += (currentNumber.toIntOrNull() ?: 0) * 60
                currentNumber = ""
            } else if (char == 'm') {
                totalMinutes += (currentNumber.toIntOrNull() ?: 0)
                currentNumber = ""
            }
        }
        
        // Add any remaining number (e.g. the "30" in "1h30") as minutes
        if (currentNumber.isNotEmpty()) {
            totalMinutes += currentNumber.toIntOrNull() ?: 0
        }
        
        return totalMinutes
    }

    fun getDefaultConfig(): String {
        return """
            # SysBlock Config
            # Add line below to lock uninstall:
            # PREVENT_UNINSTALL #Only enable if you can't controll yourself
            
            SET | MASTER_SWITCH | true
            
            # Session Times (Minutes)
            # SET | SESSION_TIME | 5 | 10 | 20 | 30
            
            # App Rules (Time: 0 = Instant Block, 30m = 30 Minutes)
            # SET | APPLOCK | com.facebook.katana | 45m
            # SET | APPLOCK | com.instagram.android | 20m
        """.trimIndent()
    }
}