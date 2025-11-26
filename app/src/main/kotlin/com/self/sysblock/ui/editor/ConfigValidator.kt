package com.self.sysblock.features.validation

data class ValidationResult(
    val errorLine: Int = -1, 
    val errorMessage: String = "", 
    val ruleCount: Int = 0,
    val hasUninstallProtection: Boolean = false
)

object ConfigValidator {
    fun validate(text: String): ValidationResult {
        val lines = text.lines()
        var ruleCount = 0
        var hasUninstallProtection = false
        val seenPackages = mutableSetOf<String>()

        lines.forEachIndexed { index, line ->
            val trimmed = line.trim()
            val lineNum = index + 1
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachIndexed

            if (trimmed == "PREVENT_UNINSTALL") {
                 hasUninstallProtection = true
                 return@forEachIndexed
            }

            val parts = trimmed.split("|").map { it.trim() }
            
            if (parts.size < 2) return ValidationResult(lineNum, "Invalid Format.")

            if (parts[0] == "SET") {
                val key = parts.getOrNull(1) ?: ""
                when (key) {
                    "MASTER_SWITCH" -> {
                        if (parts.size < 3 || parts[2].lowercase() !in listOf("true", "false")) {
                            return ValidationResult(lineNum, "Master switch must be true or false.")
                        }
                    }
                    "SESSION_TIME" -> {
                        if (parts.size < 3) return ValidationResult(lineNum, "Session times missing.")
                    }
                    "APPLOCK" -> {
                        if (parts.size < 4) return ValidationResult(lineNum, "AppLock needs: Package | Time")
                        
                        val pkg = parts[2]
                        val timeStr = parts[3]
                        
                        if (seenPackages.contains(pkg)) {
                            return ValidationResult(lineNum, "Duplicate rule for '$pkg'.")
                        }
                        seenPackages.add(pkg)
                        
                        if (timeStr.isEmpty()) return ValidationResult(lineNum, "Time cannot be empty.")
                        
                        val minutes = parseDuration(timeStr)
                        if (minutes < 5) {
                            return ValidationResult(lineNum, "Safety: Time must be at least 5m.")
                        }
                        
                        ruleCount++
                    }
                    else -> return ValidationResult(lineNum, "Unknown Command: '$key'")
                }
            } else {
                // Legacy Format Check
                if (parts.size >= 3) {
                    val pkg = parts[0]
                    if (seenPackages.contains(pkg)) return ValidationResult(lineNum, "Duplicate rule for '$pkg'.")
                    seenPackages.add(pkg)
                    ruleCount++
                }
            }
        }
        return ValidationResult(ruleCount = ruleCount, hasUninstallProtection = hasUninstallProtection)
    }

    private fun parseDuration(input: String): Int {
        input.toIntOrNull()?.let { return it }
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
        if (currentNumber.isNotEmpty()) totalMinutes += currentNumber.toIntOrNull() ?: 0
        return totalMinutes
    }
}