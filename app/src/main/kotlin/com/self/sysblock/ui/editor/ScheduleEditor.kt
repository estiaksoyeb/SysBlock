package com.self.sysblock.ui.editor

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.self.sysblock.features.freeze.FreezeManager
import java.util.Calendar

@Composable
fun FreezeSettingsDialog(
    context: Context,
    currentLineCount: Int,
    onDismiss: () -> Unit
) {
    var rules by remember { mutableStateOf(FreezeManager.getRules(context)) }
    
    var newStartH by remember { mutableStateOf("08") }
    var newStartM by remember { mutableStateOf("00") }
    var newEndH by remember { mutableStateOf("17") }
    var newEndM by remember { mutableStateOf("00") }
    var newStartLine by remember { mutableStateOf("1") }
    var newEndLine by remember { mutableStateOf("5") }

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
                Text("EDIT SCHEDULES", color = Color.Green, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 20.sp)
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
                
                HorizontalDivider(color = Color(0xFF333333))
                Spacer(modifier = Modifier.height(12.dp))

                Text("New Allowed Window:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                
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
                                val ranges = mutableListOf<IntRange>()
                                ranges.add(startLine..endLine)

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
            .background(if (isActiveNow) Color(0xFF0F1F0F) else Color(0xFF151515), RoundedCornerShape(8.dp)) 
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
        
        Switch(
            checked = rule.isEnabled,
            onCheckedChange = { onToggle() },
            enabled = isActiveNow, 
            modifier = Modifier.scale(0.8f)
        )
        
        IconButton(
            onClick = onDelete,
            enabled = isActiveNow 
        ) {
            Icon(
                Icons.Default.Delete, 
                contentDescription = "Delete", 
                tint = if (isActiveNow) Color.Red else Color.DarkGray, 
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

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

fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
)

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