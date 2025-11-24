package com.self.sysblock.ui.editor

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.self.sysblock.data.config.ConfigParser
import com.self.sysblock.features.freeze.FreezeManager
import com.self.sysblock.features.validation.ConfigValidator

@Composable
fun EditorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("SysBlockPrefs", Context.MODE_PRIVATE)
    
    val originalText = remember { prefs.getString("raw_config", ConfigParser.getDefaultConfig()) ?: "" }
    var codeText by remember { mutableStateOf(originalText) }
    
    var activeRanges by remember { mutableStateOf(FreezeManager.getActiveFrozenRanges(context)) }
    val isFreezeActive = activeRanges.isNotEmpty()
    
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf<String?>(null) } 
    var showErrorDialog by remember { mutableStateOf<String?>(null) }
    var showFreezeSettings by remember { mutableStateOf(false) }

    val isModified = codeText != originalText

    fun tryToSave() {
        val validation = ConfigValidator.validate(codeText)
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
        // Header
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
                IconButton(onClick = { showFreezeSettings = true }) {
                    Text("⚙️", fontSize = 20.sp)
                }
                TextButton(onClick = { tryToSave() }) {
                    Text("SAVE", color = if (isModified) Color.Cyan else Color.Gray, fontWeight = FontWeight.Bold)
                }
            }
        }

        HorizontalDivider(color = Color(0xFF333333))

        // Editor Content
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E))
                .verticalScroll(rememberScrollState())
        ) {
            val lineCount = codeText.lines().size
            
            // Line Numbers
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

            // Code Input
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
                                fun getLineSafe(lines: List<String>, index: Int): String = if (index < lines.size) lines[index] else ""
                                for (i in range) {
                                    if (getLineSafe(oldLines, i) != getLineSafe(newLines, i)) {
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

    // Dialogs
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
            confirmButton = { TextButton(onClick = { showUnsavedDialog = false; tryToSave() }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { showUnsavedDialog = false; onBack() }) { Text("Discard", color = Color.Red) } },
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
            confirmButton = { Button(onClick = { showErrorDialog = null }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Fix It") } },
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