package com.self.sysblock.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

object StrictSyntaxHighlighter : VisualTransformation {
    
    // Colors
    private val COLOR_COMMENT = Color(0xFF808080) // Gray
    private val COLOR_KEYWORD = Color(0xFFCC7832) // Orange (SET)
    private val COLOR_COMMAND = Color(0xFF9876AA) // Purple (APPLOCK, MASTER_SWITCH)
    private val COLOR_BOOLEAN = Color(0xFFCC7832) // Orange (true/false)
    private val COLOR_NUMBER = Color(0xFF6897BB)  // Blue
    private val COLOR_STRING = Color(0xFF6A8759)  // Green (Package names)
    private val COLOR_SEPARATOR = Color(0xFFA9B7C6) // Light Gray (|)
    private val COLOR_ERROR = Color(0xFFFF5555)   // Red
    private val COLOR_SPECIAL = Color(0xFFFF00FF) // Magenta (PREVENT_UNINSTALL)

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val annotatedString = buildAnnotatedString {
            append(raw)
            
            var currentOffset = 0
            
            raw.lines().forEach { line ->
                val lineLength = line.length
                val lineStart = currentOffset
                val lineEnd = currentOffset + lineLength
                val trimmed = line.trim()
                
                if (trimmed.isNotEmpty()) {
                    if (trimmed.startsWith("#")) {
                        // 1. COMMENT
                        addStyle(SpanStyle(color = COLOR_COMMENT), lineStart, lineEnd)
                    } else if (trimmed == "PREVENT_UNINSTALL") {
                        // 2. SPECIAL KEYWORD
                        addStyle(SpanStyle(color = COLOR_SPECIAL, fontWeight = FontWeight.Bold), lineStart, lineEnd)
                    } else if (trimmed.startsWith("SET")) {
                        // 3. SET COMMANDS
                        val parts = line.split("|")
                        var partOffset = lineStart
                        
                        parts.forEachIndexed { index, part ->
                            val partTrimmed = part.trim()
                            val partStart = line.indexOf(part, partOffset - lineStart) + lineStart
                            val partEnd = partStart + part.length
                            
                            // Separators (find '|' before this part if index > 0)
                            if (index > 0) {
                                val sepIndex = line.lastIndexOf("|", partStart - lineStart - 1) + lineStart
                                if (sepIndex >= lineStart) {
                                    addStyle(SpanStyle(color = COLOR_SEPARATOR), sepIndex, sepIndex + 1)
                                }
                            }

                            when (index) {
                                0 -> { // "SET"
                                    val keywordStart = line.indexOf("SET", partOffset - lineStart) + lineStart
                                    if (keywordStart != -1) {
                                        addStyle(SpanStyle(color = COLOR_KEYWORD, fontWeight = FontWeight.Bold), keywordStart, keywordStart + 3)
                                    }
                                }
                                1 -> { // COMMAND (APPLOCK, SESSION_TIME, etc)
                                    addStyle(SpanStyle(color = COLOR_COMMAND, fontWeight = FontWeight.Bold), partStart, partEnd)
                                }
                                else -> { // VALUES
                                    // Detect Boolean
                                    if (partTrimmed == "true" || partTrimmed == "false") {
                                        addStyle(SpanStyle(color = COLOR_BOOLEAN, fontWeight = FontWeight.Bold), partStart, partEnd)
                                    } 
                                    // Detect Numbers (including 30m, 1h)
                                    else if (partTrimmed.matches(Regex("[0-9]+[hm]?"))) {
                                        addStyle(SpanStyle(color = COLOR_NUMBER), partStart, partEnd)
                                    }
                                    // Detect Package Names (contains dots)
                                    else if (partTrimmed.contains(".")) {
                                        addStyle(SpanStyle(color = COLOR_STRING), partStart, partEnd)
                                    }
                                    // Default
                                    else {
                                        addStyle(SpanStyle(color = Color.LightGray), partStart, partEnd)
                                    }
                                }
                            }
                            partOffset = partEnd
                        }
                    } else {
                        // 4. LEGACY / UNKNOWN (Simple highlighting)
                        if (trimmed.contains("|")) {
                            // Legacy: package | 0 | true
                            val parts = line.split("|")
                            var partOffset = lineStart
                            
                            parts.forEachIndexed { index, part ->
                                val partTrimmed = part.trim()
                                val partStart = line.indexOf(part, partOffset - lineStart) + lineStart
                                val partEnd = partStart + part.length
                                
                                if (index > 0) {
                                    val sepIndex = line.lastIndexOf("|", partStart - lineStart - 1) + lineStart
                                    if (sepIndex >= lineStart) addStyle(SpanStyle(color = COLOR_SEPARATOR), sepIndex, sepIndex + 1)
                                }

                                when (index) {
                                    0 -> addStyle(SpanStyle(color = COLOR_STRING), partStart, partEnd) // Package
                                    1 -> addStyle(SpanStyle(color = COLOR_NUMBER), partStart, partEnd) // Limit
                                    2 -> addStyle(SpanStyle(color = COLOR_BOOLEAN), partStart, partEnd) // Boolean
                                }
                                partOffset = partEnd
                            }
                        } else {
                            // Invalid/Unknown text
                            addStyle(SpanStyle(color = COLOR_ERROR), lineStart, lineEnd)
                        }
                    }
                }
                
                currentOffset += lineLength + 1 // +1 for newline
            }
        }
        return TransformedText(annotatedString, OffsetMapping.Identity)
    }
}