package com.self.sysblock.ui.home

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.self.sysblock.data.config.SystemConfig
import com.self.sysblock.receivers.AdminReceiver

@Composable
fun SystemControlPanel(
    context: Context,
    isAccessibilityEnabled: Boolean,
    isUsageEnabled: Boolean,
    isAdminActive: Boolean,
    parsedConfig: SystemConfig,
    isMasterSwitchFrozen: Boolean,
    onToggleMaster: (Boolean) -> Unit
) {
    var showHelpDialog by remember { mutableStateOf(false) }

    Column {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SYSBLOCK // HOME",
                color = Color.Green,
                fontSize = 24.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            
            IconButton(onClick = { showHelpDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Help",
                    tint = Color.Gray,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Permissions Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("System Permissions", color = Color.Gray, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                
                PermissionRow(
                    text = if (isAccessibilityEnabled) "✅ Accessibility Active" else "❌ Enable Accessibility",
                    isActive = isAccessibilityEnabled,
                    onClick = { if (!isAccessibilityEnabled) context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                PermissionRow(
                    text = if (isUsageEnabled) "✅ Usage Access Active" else "❌ Enable Usage Access",
                    isActive = isUsageEnabled,
                    onClick = { if (!isUsageEnabled) context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                )

                if (parsedConfig.preventUninstall) {
                    Spacer(modifier = Modifier.height(8.dp))
                    PermissionRow(
                        text = if (isAdminActive) "✅ Uninstall Protection Active" else "❌ Enable Uninstall Protection",
                        isActive = isAdminActive,
                        onClick = {
                            if (!isAdminActive) {
                                try {
                                    val cn = ComponentName(context, AdminReceiver::class.java)
                                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, cn)
                                        putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Protects SysBlock from being removed.")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                    e.printStackTrace()
                                }
                            }
                        }
                    )
                }
            }
        }

        // Master Switch Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Master Switch", color = Color.White, fontWeight = FontWeight.Bold)
                        if (isMasterSwitchFrozen) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.Lock, contentDescription = "Frozen", tint = Color.Cyan, modifier = Modifier.size(16.dp))
                            Text(" FROZEN", color = Color.Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        text = if (isAccessibilityEnabled && isUsageEnabled) "System Ready" else "Permissions Missing",
                        color = Color.Gray, 
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = parsedConfig.masterSwitch,
                    onCheckedChange = onToggleMaster,
                    enabled = isAccessibilityEnabled && isUsageEnabled && !isMasterSwitchFrozen,
                    colors = SwitchDefaults.colors(
                        disabledCheckedTrackColor = Color(0xFF004400),
                        disabledCheckedThumbColor = Color.Gray
                    )
                )
            }
        }
    }

    if (showHelpDialog) {
        HelpDialog(onDismiss = { showHelpDialog = false })
    }
}

@Composable
fun PermissionRow(text: String, isActive: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            color = if (isActive) Color.Green else Color.Red,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "SYSTEM MANUAL", 
                    color = Color.Green, 
                    fontSize = 20.sp, 
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(24.dp))
                
                Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    HelpSection(
                        "1. App Lock Rules", 
                        "Format: SET | APPLOCK | package | time\nExample: SET | APPLOCK | com.facebook.katana | 30m\n(Time can be '30m', '1h', '1h30m')"
                    )
                    HelpSection(
                        "2. Session Times", 
                        "Customize the timer buttons:\nSET | SESSION_TIME | 5 | 10 | 20 | 30\n(Values are in minutes)"
                    )
                    HelpSection(
                        "3. Security", 
                        "Add 'PREVENT_UNINSTALL' on a new line to activate self-protection.\nThis prevents force-stop and uninstalling."
                    )
                    HelpSection(
                        "4. Freeze Protocols", 
                        "Use the Gear icon ⚙️ in the editor to set times when the config cannot be edited."
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
                ) {
                    Text("ACKNOWLEDGE", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun HelpSection(title: String, content: String) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(content, color = Color.Gray, fontSize = 14.sp, lineHeight = 20.sp)
    }
}