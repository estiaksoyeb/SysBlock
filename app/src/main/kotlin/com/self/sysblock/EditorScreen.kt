package com.self.sysblock

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.util.Calendar

@Composable
fun EditorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("SysBlockPrefs", Context.MODE_PRIVATE)
    
    // Config State
    val originalText = remember { prefs.getString("raw_config", ConfigParser.getDefaultConfig()) ?: "" }
    var codeText by remember { mutableStateOf(originalText) }
    
    // Freeze/Lock State
    var activeRanges by remember { mutableStateOf(FreezeManager.getActiveFrozenRanges(context)) }
    val isFreezeActive = activeRanges.isNotEmpty()
    
    // Dialogs
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf<String?>(null) } 
    var showErrorDialog by remember { mutableStateOf<String?>(null) }
    var showFreezeSettings by remember { mutableStateOf(false) }

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
                
                if (isFreezeActive) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("🔒 FROZEN", color = Color.Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Settings Icon
                IconButton(onClick = { showFreezeSettings = true }) {
                    Text("⚙️", fontSize = 20.sp)
                }
                TextButton(onClick = { tryToSave() }) {
                    Text("SAVE", color = if (isModified) Color.Cyan else Color.Gray, fontWeight = FontWeight.Bold)
                }
            }
        }

        Divider(color = Color(0xFF333333))

        // --- EDITOR AREA ---
        val verticalScrollState = rememberScrollState()
        
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E))
                .verticalScroll(verticalScrollState)
        ) {
            val lineCount = codeText.lines().size
            
            // 1. Line Numbers
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

            // 2. Code Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
            ) {
                BasicTextField(
                    value = codeText,
                    onValueChange = { newText ->
                        if (isFreezeActive) {
                            val oldLines = codeText.lines()
                            val newLines = newText.lines()
                            var isBlocked = false
                            for (range in activeRanges) {
                                fun getLineSafe(lines: List<String>, index: Int): String {
                                    return if (index < lines.size) lines[index] else ""
                                }
                                for (i in range) {
                                    val oldLineContent = getLineSafe(oldLines, i)
                                    val newLineContent = getLineSafe(newLines, i)
                                    if (oldLineContent != newLineContent) {
                                        isBlocked = true
                                        break
                                    }
                                }
                                if (isBlocked) break
                            }
                            if (!isBlocked) codeText = newText
                        } else {
                            codeText = newText
                        }
                    },
                    textStyle = TextStyle(
                        color = Color.LightGray,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        lineHeight = 20.sp 
                    ),
                    cursorBrush = SolidColor(Color.Green),
                    visualTransformation = StrictSyntaxHighlighter, 
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp)
                )
            }
        }
    }

    // --- DIALOGS ---
    if (showFreezeSettings) {
        FreezeSettingsDialog(
            context = context,
            currentLineCount = codeText.lines().size, 
            onDismiss = { 
                showFreezeSettings = false 
                activeRanges = FreezeManager.getActiveFrozenRanges(context)
            }
        )
    }

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

// --- SETTINGS POPUP UI ---
@Composable
fun FreezeSettingsDialog(
    context: Context,
    currentLineCount: Int,
    onDismiss: () -> Unit
) {
    var rules by remember { mutableStateOf(FreezeManager.getRules(context)) }
    
    // State for creating a NEW rule
    var newStartH by remember { mutableStateOf("08") }
    var newStartM by remember { mutableStateOf("00") }
    var newEndH by remember { mutableStateOf("17") }
    var newEndM by remember { mutableStateOf("00") }
    var newStartLine by remember { mutableStateOf("1") }
    var newEndLine by remember { mutableStateOf("5") }

    // --- VALIDATION LOGIC ---
    val startH = newStartH.toIntOrNull() ?: -1
    val startM = newStartM.toIntOrNull() ?: -1
    val endH = newEndH.toIntOrNull() ?: -1
    val endM = newEndM.toIntOrNull() ?: -1
    val startLine = newStartLine.toIntOrNull() ?: -1
    val endLine = newEndLine.toIntOrNull() ?: -1

    val isTimeValid = (startH in 0..23) && (startM in 0..59) && (endH in 0..23) && (endM in 0..59)
    val isLineValid = (startLine > 0) && (endLine >= startLine) && (endLine <= currentLineCount)
    
    val startTotalMins = startH * 60 + startM
    val endTotalMins = endH * 60 + endM
    val isSequenceValid = endTotalMins > startTotalMins

    // --- CONFLICT DETECTION (STRICT) ---
    // We check against ALL existing rules. A line can only be managed by ONE rule.
    val allManagedLines = rules.flatMap { (it.startLine..it.endLine).toList() }.toSet()
    
    val requestedLines = if (isLineValid) (startLine..endLine).toSet() else emptySet()
    
    val conflictingLines = requestedLines.intersect(allManagedLines)
    val hasConflict = conflictingLines.isNotEmpty()

    val canAdd = isTimeValid && isLineValid && isSequenceValid && !hasConflict

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "EDIT SCHEDULES", 
                    color = Color.Green, 
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 20.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Active Windows:", color = Color.Gray, fontSize = 12.sp)
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(rules) { rule ->
                        val isRuleActiveNow = isRuleActiveNow(rule)
                        RuleItem(
                            rule = rule, 
                            isActiveNow = isRuleActiveNow,
                            onDelete = {
                                rules = rules.toMutableList().apply { remove(rule) }
                                FreezeManager.saveRules(context, rules)
                            }, 
                            onToggle = {
                                val idx = rules.indexOf(rule)
                                if (idx != -1) {
                                    val updated = rule.copy(isEnabled = !rule.isEnabled)
                                    rules = rules.toMutableList().apply { set(idx, updated) }
                                    FreezeManager.saveRules(context, rules)
                                }
                            }
                        )
                    }
                }
                
                Divider(color = Color(0xFF333333))
                Spacer(modifier = Modifier.height(12.dp))

                // --- ADD NEW RULE SECTION ---
                Text("New Allowed Window:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                
                // Time Input
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (!isSequenceValid && isTimeValid) "End time must be after Start" else "Allowed Time (00:00 - 23:59)", 
                            color = if (!isSequenceValid && isTimeValid) Color.Red else Color.Gray, 
                            fontSize = 10.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TimeInput(newStartH) { newStartH = it }
                            Text(":", color = Color.Gray)
                            TimeInput(newStartM) { newStartM = it }
                            Text(" -> ", color = Color.Gray)
                            TimeInput(newEndH) { newEndH = it }
                            Text(":", color = Color.Gray)
                            TimeInput(newEndM) { newEndM = it }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Line Input
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        val helperText = when {
                            hasConflict -> "Lines already managed by another rule"
                            !isLineValid -> "Invalid Lines (Max: $currentLineCount)"
                            else -> "Lines (Start - End)"
                        }
                        
                        Text(
                            helperText, 
                            color = if (!isLineValid || hasConflict) Color.Red else Color.Gray, 
                            fontSize = 10.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TimeInput(newStartLine) { newStartLine = it }
                            Text(" - ", color = Color.Gray)
                            TimeInput(newEndLine) { newEndLine = it }
                        }
                    }
                    
                    IconButton(
                        onClick = {
                            if (canAdd) {
                                val sortedLines = requestedLines.sorted()
                                val ranges = mutableListOf<IntRange>()
                                
                                if (sortedLines.isNotEmpty()) {
                                    var rangeStart = sortedLines[0]
                                    var rangeEnd = sortedLines[0]
                                    
                                    for (i in 1 until sortedLines.size) {
                                        if (sortedLines[i] == rangeEnd + 1) {
                                            rangeEnd = sortedLines[i]
                                        } else {
                                            ranges.add(rangeStart..rangeEnd)
                                            rangeStart = sortedLines[i]
                                            rangeEnd = sortedLines[i]
                                        }
                                    }
                                    ranges.add(rangeStart..rangeEnd)
                                }

                                val newRulesList = rules.toMutableList()
                                for (range in ranges) {
                                    val rule = FreezeManager.FreezeRule(
                                        startHour = startH, startMinute = startM,
                                        endHour = endH, endMinute = endM,
                                        startLine = range.first, endLine = range.last
                                    )
                                    newRulesList.add(rule)
                                }
                                rules = newRulesList
                                FreezeManager.saveRules(context, rules)
                            }
                        },
                        enabled = canAdd,
                        modifier = Modifier
                            .background(
                                if (canAdd) Color(0xFF004400) else Color(0xFF222222), 
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                1.dp,
                                if (canAdd) Color.Green else Color.DarkGray,
                                RoundedCornerShape(8.dp)
                            )
                    ) {
                        Icon(
                            Icons.Default.Add, 
                            contentDescription = "Add", 
                            tint = if (canAdd) Color.Green else Color.Gray
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
                ) {
                    Text("CLOSE MANAGER")
                }
            }
        }
    }
}

fun isRuleActiveNow(rule: FreezeManager.FreezeRule): Boolean {
    val now = Calendar.getInstance()
    val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    val startMinutes = rule.startHour * 60 + rule.startMinute
    val endMinutes = rule.endHour * 60 + rule.endMinute

    return if (startMinutes < endMinutes) {
        currentMinutes in startMinutes until endMinutes
    } else {
        currentMinutes >= startMinutes || currentMinutes < endMinutes
    }
}

@Composable
fun RuleItem(
    rule: FreezeManager.FreezeRule, 
    isActiveNow: Boolean, 
    onDelete: () -> Unit, 
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isActiveNow) Color(0xFF0F1F0F) else Color(0xFF151515), RoundedCornerShape(8.dp)) // Dark Green if Active
            .border(
                1.dp, 
                if (isActiveNow && rule.isEnabled) Color.Green else Color.Transparent, 
                RoundedCornerShape(8.dp)
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = String.format("%02d:%02d -> %02d:%02d", rule.startHour, rule.startMinute, rule.endHour, rule.endMinute),
                    color = if (rule.isEnabled) Color.White else Color.Gray,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                if (rule.isEnabled) {
                    Spacer(modifier = Modifier.width(8.dp))
                    if (isActiveNow) {
                        Text("UNLOCKED", color = Color.Green, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Icon(
                            imageVector = Icons.Default.Check, 
                            contentDescription = "Editable",
                            tint = Color.Green,
                            modifier = Modifier.size(12.dp).padding(start = 2.dp)
                        )
                    } else {
                         Text("LOCKED", color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                         Icon(
                            imageVector = Icons.Default.Lock, 
                            contentDescription = "Locked",
                            tint = Color.Red,
                            modifier = Modifier.size(12.dp).padding(start = 2.dp)
                        )
                    }
                }
            }
            Text(
                text = "Lines ${rule.startLine} - ${rule.endLine}",
                color = if (isActiveNow && rule.isEnabled) Color.Green else Color.Gray,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        
        // --- LOCK ENFORCEMENT ---
        // You cannot Toggle OR Delete a schedule if it is currently locked.
        
        Switch(
            checked = rule.isEnabled,
            onCheckedChange = { onToggle() },
            enabled = isActiveNow, // ONLY ENABLED IF UNLOCKED
            modifier = Modifier.scale(0.8f)
        )
        
        IconButton(
            onClick = onDelete,
            enabled = isActiveNow // ONLY ENABLED IF UNLOCKED
        ) {
            Icon(
                Icons.Default.Delete, 
                contentDescription = "Delete", 
                tint = if (isActiveNow) Color.Red else Color.DarkGray, // Gray out if locked
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
)

@Composable
fun TimeInput(value: String, onValueChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = { if (it.length <= 3) onValueChange(it) },
        textStyle = TextStyle(color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier
            .width(40.dp)
            .background(Color(0xFF333333), RoundedCornerShape(4.dp))
            .padding(4.dp)
    )
}

data class ValidationResult(val errorLine: Int = -1, val errorMessage: String = "", val summary: String = "")

// --- MODIFIED VALIDATOR ---
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

        // THE FIX: Explicitly allow PREVENT_UNINSTALL
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

// --- MODIFIED HIGHLIGHTER ---
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
                } else if (trimmed != "PREVENT_UNINSTALL") { // Don't overwrite our special color
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
