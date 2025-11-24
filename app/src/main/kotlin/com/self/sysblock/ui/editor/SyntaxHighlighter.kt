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
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val annotatedString = buildAnnotatedString {
            append(raw)
            var currentOffset = 0
            val packageCounts = raw.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .mapNotNull { 
                    val parts = it.split("|")
                    if (parts.size >= 3 && parts[0].trim() != "SET") parts[0].trim() else null 
                }
                .groupingBy { it }
                .eachCount()

            raw.lines().forEach { line ->
                val lineLength = line.length
                val lineStart = currentOffset
                val lineEnd = currentOffset + lineLength
                val trimmed = line.trim()
                var isError = false
                
                if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    
                    // HIGHLIGHTING FIX FOR PREVENT_UNINSTALL
                    if (trimmed == "PREVENT_UNINSTALL") {
                        addStyle(SpanStyle(color = Color.Magenta, fontWeight = FontWeight.Bold), lineStart, lineEnd)
                    } else {
                        val parts = trimmed.split("|")
                        if (parts.size >= 3) {
                            val col1 = parts[0].trim()
                            val col2 = parts[1].trim()
                            val col3 = parts[2].trim()
                            if (col1 == "SET") {
                                if (!col3.equals("true", true) && !col3.equals("false", true)) isError = true
                            } else {
                                val isNum = col2.toIntOrNull() != null
                                val isBool = col3.equals("true", true) || col3.equals("false", true)
                                val isDuplicate = packageCounts.getOrDefault(col1, 0) > 1
                                if (!isNum || !isBool || isDuplicate) isError = true
                            }
                        } else {
                            // Only mark as error if it is NOT the special keyword we just handled above
                            isError = true
                        }
                    }
                }

                if (isError) {
                    addStyle(SpanStyle(color = Color(0xFFFF5555)), lineStart, lineEnd)
                } else if (trimmed != "PREVENT_UNINSTALL") { 
                    if (trimmed.startsWith("#")) {
                        addStyle(SpanStyle(color = Color.Gray), lineStart, lineEnd)
                    } else {
                        Regex("\\bSET\\b").findAll(line).forEach {
                            addStyle(SpanStyle(color = Color(0xFFCC7832), fontWeight = FontWeight.Bold), lineStart + it.range.first, lineStart + it.range.last + 1)
                        }
                        Regex("(?i)\\btrue\\b").findAll(line).forEach {
                            addStyle(SpanStyle(color = Color(0xFF6A8759)), lineStart + it.range.first, lineStart + it.range.last + 1)
                        }
                        Regex("(?i)\\bfalse\\b").findAll(line).forEach {
                            addStyle(SpanStyle(color = Color(0xFFCC666E)), lineStart + it.range.first, lineStart + it.range.last + 1)
                        }
                        Regex("\\|").findAll(line).forEach {
                            addStyle(SpanStyle(color = Color(0xFF569CD6)), lineStart + it.range.first, lineStart + it.range.last + 1)
                        }
                        Regex("\\b\\d+\\b").findAll(line).forEach {
                            addStyle(SpanStyle(color = Color(0xFF6897BB)), lineStart + it.range.first, lineStart + it.range.last + 1)
                        }
                    }
                }
                currentOffset += lineLength + 1 
            }
        }
        return TransformedText(annotatedString, OffsetMapping.Identity)
    }
}