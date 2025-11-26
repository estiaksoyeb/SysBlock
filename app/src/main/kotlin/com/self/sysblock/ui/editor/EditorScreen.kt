package com.self.sysblock.ui.editor

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.self.sysblock.data.config.ConfigParser
import com.self.sysblock.features.freeze.FreezeManager
import com.self.sysblock.features.validation.ConfigValidator
import com.self.sysblock.features.validation.ValidationResult
import com.self.sysblock.receivers.AdminReceiver

@Composable
fun EditorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = context.getSharedPreferences("SysBlockPrefs", Context.MODE_PRIVATE)
    
    val originalText = remember { prefs.getString("raw_config", ConfigParser.getDefaultConfig()) ?: "" }
    var codeText by remember { mutableStateOf(originalText) }
    var editorFontSize by remember { mutableStateOf(14.sp) }
    
    var activeRanges by remember { mutableStateOf(FreezeManager.getActiveFrozenRanges(context)) }
    val isFreezeActive = activeRanges.isNotEmpty()
    
    // Dialog States
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var validationResult by remember { mutableStateOf<ValidationResult?>(null) } // Replaces string-based dialog
    var showErrorDialog by remember { mutableStateOf<String?>(null) }
    var showFreezeSettings by remember { mutableStateOf(false) }

    // Admin State
    var isAdminActive by remember { mutableStateOf(false) }
    val dpm = remember { context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager }
    val adminComponent = remember { ComponentName(context, AdminReceiver::class.java) }

    // Check Admin Status on Resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAdminActive = dpm.isAdminActive(adminComponent)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isModified = codeText != originalText

    fun tryToSave() {
        val result = ConfigValidator.validate(codeText)
        if (result.errorLine != -1) {
            showErrorDialog = "Error on Line ${result.errorLine}:\n${result.errorMessage}"
        } else {
            validationResult = result
        }
    }

    fun zoomEditor(zoomIn: Boolean) {
        val currentVal = editorFontSize.value
        val newVal = if (zoomIn) currentVal + 2f else currentVal - 2f
        editorFontSize = newVal.coerceIn(10f, 32f).sp
    }

    BackHandler {
        if (isModified) showUnsavedDialog = true else onBack()
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF121212)) 
    ) {
        // --- HEADER ---
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF1E1E1E)).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("CONFIG.SYS", color = Color.Green, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                if (isFreezeActive) {
                    Text("🔒 FROZEN", color = Color.Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isModified) Text("*", color = Color.Yellow, modifier = Modifier.padding(end = 8.dp))
                IconButton(onClick = { zoomEditor(false) }) { Text("A-", color = Color.LightGray, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                IconButton(onClick = { zoomEditor(true) }) { Text("A+", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                IconButton(onClick = { showFreezeSettings = true }) { Text("⚙️", fontSize = 20.sp) }
                TextButton(onClick = { tryToSave() }) { Text("SAVE", color = if (isModified) Color.Cyan else Color.Gray, fontWeight = FontWeight.Bold) }
            }
        }

        HorizontalDivider(color = Color(0xFF333333))

        // --- EDITOR AREA ---
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFF1E1E1E)).verticalScroll(rememberScrollState())
        ) {
            val lineCount = codeText.lines().size
            Text(
                text = (1..lineCount).joinToString("\n"),
                color = Color.Gray,
                fontSize = editorFontSize,
                fontFamily = FontFamily.Monospace,
                lineHeight = editorFontSize * 1.5,
                textAlign = TextAlign.End,
                modifier = Modifier.width(40.dp).padding(top = 8.dp, end = 8.dp).background(Color(0xFF252525))
            )

            Box(modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
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
                        color = Color.LightGray, fontFamily = FontFamily.Monospace, fontSize = editorFontSize, lineHeight = editorFontSize * 1.5
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
            codeLines = codeText.lines(),
            onDismiss = { showFreezeSettings = false; activeRanges = FreezeManager.getActiveFrozenRanges(context) }
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

    // --- CONFIRMATION DIALOG ---
    if (validationResult != null) {
        val result = validationResult!!
        AlertDialog(
            onDismissRequest = { validationResult = null },
            title = { Text("Confirm Update", color = Color.Green) },
            text = { 
                // Scrollable container for the dialog content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp) // Max height constraint
                        .verticalScroll(rememberScrollState())
                ) {
                    Text("Configuration Analysis:", color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Simple Summary
                    Text("• Valid Syntax", color = Color.White)
                    Text("• ${result.ruleCount} Active Rules", color = Color.White)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color.DarkGray)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Uninstall Protection Logic
                    if (result.hasUninstallProtection) {
                        Text("🛡️ UNINSTALL PROTECTION", color = Color.Cyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (isAdminActive) {
                            Text("✅ Device Admin is Active.", color = Color.Green)
                            Text("The app cannot be uninstalled while this config is active.", color = Color.Gray, fontSize = 12.sp)
                        } else {
                            Text("⚠️ Device Admin NOT Active", color = Color(0xFFFF8800), fontWeight = FontWeight.Bold)
                            Text("Protection will NOT work until you enable it.", color = Color.LightGray, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                                            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Protects SysBlock from being removed.")
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF004400))
                            ) {
                                Text("Enable Device Admin", color = Color.Green)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("To disable, add '#' before PREVENT_UNINSTALL.", color = Color.Gray, fontSize = 10.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    } else {
                        if (isAdminActive) {
                            Text("Device Admin is active but not enforced by config.", color = Color.Gray, fontSize = 12.sp)
                        } else {
                            Text("Uninstall Protection is OFF.", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { 
                        prefs.edit().putString("raw_config", codeText).apply()
                        Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                        validationResult = null
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006600))
                ) { Text("Apply Config") }
            },
            dismissButton = { TextButton(onClick = { validationResult = null }) { Text("Cancel") } },
            containerColor = Color(0xFF222222)
        )
    }
}