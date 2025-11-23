package com.self.sysblock

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EditorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("SysBlockPrefs", Context.MODE_PRIVATE)
    
    val originalText = remember { 
        prefs.getString("raw_config", ConfigParser.getDefaultConfig()) ?: "" 
    }
    var codeText by remember { mutableStateOf(originalText) }
    
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf<String?>(null) } 
    var showErrorDialog by remember { mutableStateOf<String?>(null) }

    val isModified = codeText != originalText

    fun tryToSave() {
        val validation = validateConfig(codeText)
        if (validation.errorLine != -1) {
            showErrorDialog = "Error on Line ${validation.errorLine}:\n${validation.errorMessage}"
        } else {
            showConfirmDialog = validation.summary
        }
    }

    BackHandler {
        if (isModified) showUnsavedDialog = true else onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212)) 
    ) {
        // --- HEADER ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("CONFIG.SYS", color = Color.Green, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                if (isModified) Text("*", color = Color.Yellow, modifier = Modifier.padding(start = 4.dp))
            }
            TextButton(onClick = { tryToSave() }) {
                Text("SAVE", color = if (isModified) Color.Cyan else Color.Gray, fontWeight = FontWeight.Bold)
            }
        }

        Divider(color = Color(0xFF333333))

        // --- EDITOR AREA ---
        val scrollState = rememberScrollState()
        
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E))
                .verticalScroll(scrollState)
        ) {
            val lineCount = codeText.lines().size
            Text(
                text = (1..lineCount).joinToString("\n"),
                color = Color.Gray,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 20.sp, 
                textAlign = TextAlign.End,
                modifier = Modifier
                    .width(40.dp)
                    .padding(top = 8.dp, end = 8.dp)
                    .background(Color(0xFF252525))
            )

            BasicTextField(
                value = codeText,
                onValueChange = { codeText = it },
                textStyle = TextStyle(
                    color = Color.LightGray,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    lineHeight = 20.sp 
                ),
                cursorBrush = SolidColor(Color.Green),
                visualTransformation = StrictSyntaxHighlighter, 
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 8.dp, start = 4.dp, end = 4.dp)
            )
        }
    }

    // --- DIALOGS ---
    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text("Unsaved Changes") },
            text = { Text("Save before exiting?") },
            confirmButton = {
                TextButton(onClick = { showUnsavedDialog = false; tryToSave() }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showUnsavedDialog = false; onBack() }) { Text("Discard", color = Color.Red) }
            },
            containerColor = Color(0xFF222222),
            titleContentColor = Color.White,
            textContentColor = Color.LightGray
        )
    }

    if (showErrorDialog != null) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = null },
            title = { Text("Syntax Error", color = Color.Red) },
            text = { Text(showErrorDialog!!, fontFamily = FontFamily.Monospace, color = Color.White) },
            confirmButton = {
                Button(onClick = { showErrorDialog = null }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Fix It") }
            },
            containerColor = Color(0xFF222222)
        )
    }

    if (showConfirmDialog != null) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = null },
            title = { Text("Confirm Update", color = Color.Green) },
            text = { 
                Column {
                    Text("Configuration Analysis:", color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(showConfirmDialog!!, fontFamily = FontFamily.Monospace, color = Color.White, fontSize = 13.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = { 
                        prefs.edit().putString("raw_config", codeText).apply()
                        Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                        showConfirmDialog = null
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006600))
                ) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showConfirmDialog = null }) { Text("Cancel") } },
            containerColor = Color(0xFF222222)
        )
    }
}

// --- LOGIC: VALIDATOR (Strict on Save) ---
data class ValidationResult(val errorLine: Int = -1, val errorMessage: String = "", val summary: String = "")

fun validateConfig(text: String): ValidationResult {
    val lines = text.lines()
    var ruleCount = 0
    var strictCount = 0
    val summaryBuilder = StringBuilder()
    val seenPackages = mutableSetOf<String>()

    lines.forEachIndexed { index, line ->
        val trimmed = line.trim()
        val lineNum = index + 1
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachIndexed

        val parts = trimmed.split("|").map { it.trim() }
        
        // Allow extensions: Size must be AT LEAST 3
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
            
            // Duplicate Check
            if (seenPackages.contains(pkg)) {
                return ValidationResult(lineNum, "Duplicate rule for '$pkg'.")
            }
            seenPackages.add(pkg)
            
            if (time == null) return ValidationResult(lineNum, "Time limit must be a number.")
            if (strict != "true" && strict != "false") return ValidationResult(lineNum, "Strict mode must be 'true' or 'false'.")
            
            ruleCount++
            if (parts[2].toBoolean()) strictCount++
            
            // Show optional 4th column in summary if it exists
            val extra = if (parts.size > 3) " + Extended" else ""
            summaryBuilder.append("• ${pkg.takeLast(15)} ($time m)$extra\n")
        }
    }
    return ValidationResult(summary = "Rules: $ruleCount\nStrict: $strictCount\n\n$summaryBuilder")
}

// --- LOGIC: HIGHLIGHTER (Forgiving while typing) ---
object StrictSyntaxHighlighter : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val annotatedString = buildAnnotatedString {
            append(raw)

            var currentOffset = 0
            
            // Pre-pass: Count duplicates ONLY for complete lines to avoid flagging half-typed lines
            val packageCounts = raw.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .mapNotNull { 
                    val parts = it.split("|")
                    // Only count as duplicate if line is "Structure Complete" (>= 3 parts)
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
                    val parts = trimmed.split("|")
                    
                    // CHANGE 1: Don't mark RED if parts < 3 (Incomplete/Typing)
                    if (parts.size >= 3) {
                        val col1 = parts[0].trim()
                        val col2 = parts[1].trim()
                        val col3 = parts[2].trim()

                        if (col1 == "SET") {
                            if (!col3.equals("true", true) && !col3.equals("false", true)) isError = true
                        } else {
                            // Type Checks
                            val isNum = col2.toIntOrNull() != null
                            val isBool = col3.equals("true", true) || col3.equals("false", true)
                            
                            // Duplicate Check
                            val isDuplicate = packageCounts.getOrDefault(col1, 0) > 1
                            
                            if (!isNum || !isBool || isDuplicate) isError = true
                        }
                    }
                    // If parts < 3, isError remains false (Neutral color)
                }

                if (isError) {
                    addStyle(SpanStyle(color = Color(0xFFFF5555)), lineStart, lineEnd)
                } else {
                    // Syntax Coloring
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