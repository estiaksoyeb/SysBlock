package com.self.sysblock.features.validation

data class ValidationResult(
    val errorLine: Int = -1, 
    val errorMessage: String = "", 
    val summary: String = ""
)

object ConfigValidator {
    fun validate(text: String): ValidationResult {
        val lines = text.lines()
        var ruleCount = 0
        var strictCount = 0
        val summaryBuilder = StringBuilder()
        val seenPackages = mutableSetOf<String>()

        lines.forEachIndexed { index, line ->
            val trimmed = line.trim()
            val lineNum = index + 1
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachIndexed

            if (trimmed == "PREVENT_UNINSTALL") {
                 summaryBuilder.append("• Uninstall Protection Active\n")
                 return@forEachIndexed
            }

            val parts = trimmed.split("|").map { it.trim() }
            if (parts.size < 3) return ValidationResult(lineNum, "Format Error. Incomplete rule.")

            if (parts[0] == "SET") {
                val key = parts[1]
                val value = parts[2]
                if (key == "MASTER_SWITCH" && value.lowercase() !in listOf("true", "false")) {
                    return ValidationResult(lineNum, "Master switch must be true or false.")
                }
            } else {
                val pkg = parts[0]
                val time = parts[1].toIntOrNull()
                val strict = parts[2].lowercase()
                
                if (seenPackages.contains(pkg)) {
                    return ValidationResult(lineNum, "Duplicate rule for '$pkg'.")
                }
                seenPackages.add(pkg)
                
                if (time == null) return ValidationResult(lineNum, "Time limit must be a number.")
                if (strict != "true" && strict != "false") return ValidationResult(lineNum, "Strict mode must be 'true' or 'false'.")
                
                ruleCount++
                if (parts[2].toBoolean()) strictCount++
                
                val extra = if (parts.size > 3) " + Extended" else ""
                summaryBuilder.append("• ${pkg.takeLast(15)} ($time m)$extra\n")
            }
        }
        return ValidationResult(summary = "Rules: $ruleCount\nStrict: $strictCount\n\n$summaryBuilder")
    }
}