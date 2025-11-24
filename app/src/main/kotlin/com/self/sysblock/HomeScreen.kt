package com.self.sysblock

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import android.provider.Settings
import android.text.TextUtils.SimpleStringSplitter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
fun HomeScreen(onNavigateToEditor: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = context.getSharedPreferences("SysBlockPrefs", Context.MODE_PRIVATE)

    var isAccessibilityEnabled by remember { mutableStateOf(false) }
    var isUsageEnabled by remember { mutableStateOf(false) }
    var isAdminActive by remember { mutableStateOf(false) }
    
    var rawConfig by remember { mutableStateOf("") }
    var parsedConfig by remember { mutableStateOf(ConfigParser.SystemConfig()) }
    
    // Freeze State
    var activeFrozenRanges by remember { mutableStateOf(emptyList<IntRange>()) }
    
    var showHelpDialog by remember { mutableStateOf(false) }

    fun checkAdminStatus(): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val componentName = ComponentName(context, AdminReceiver::class.java)
        return dpm.isAdminActive(componentName)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = isAccessibilityServiceEnabled(context)
                isUsageEnabled = isUsageAccessGranted(context) 
                isAdminActive = checkAdminStatus()
                rawConfig = prefs.getString("raw_config", ConfigParser.getDefaultConfig()) ?: ""
                parsedConfig = ConfigParser.parse(rawConfig)
                activeFrozenRanges = FreezeManager.getActiveFrozenRanges(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun toggleMasterSwitch(newValue: Boolean) {
        val newConfigText = if (rawConfig.contains("SET | MASTER_SWITCH")) {
            rawConfig.lines().joinToString("\n") { line ->
                if (line.trim().startsWith("SET | MASTER_SWITCH")) {
                    "SET | MASTER_SWITCH | $newValue"
                } else line
            }
        } else {
            "SET | MASTER_SWITCH | $newValue\n" + rawConfig
        }
        prefs.edit().putString("raw_config", newConfigText).apply()
        rawConfig = newConfigText
        parsedConfig = ConfigParser.parse(newConfigText)
    }
    
    val masterSwitchLineIndex = remember(rawConfig) {
        rawConfig.lines().indexOfFirst { it.trim().startsWith("SET | MASTER_SWITCH") }
    }
    
    val isMasterSwitchFrozen = remember(masterSwitchLineIndex, activeFrozenRanges) {
        if (masterSwitchLineIndex != -1) {
            activeFrozenRanges.any { range -> masterSwitchLineIndex in range }
        } else {
            false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp)
    ) {
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

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("System Permissions", color = Color.Gray, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable {
                        if (!isAccessibilityEnabled) context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                ) {
                    Text(
                        text = if (isAccessibilityEnabled) "✅ Accessibility Active" else "❌ Enable Accessibility",
                        color = if (isAccessibilityEnabled) Color.Green else Color.Red,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable {
                        if (!isUsageEnabled) context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                ) {
                    Text(
                        text = if (isUsageEnabled) "✅ Usage Access Active" else "❌ Enable Usage Access",
                        color = if (isUsageEnabled) Color.Green else Color.Red,
                        modifier = Modifier.weight(1f)
                    )
                }

                                if (parsedConfig.preventUninstall) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            if (!isAdminActive) {
                                try {
                                    // Use Hardcoded Strings to ensure package path is 100% correct
                                    val cn = ComponentName("com.self.sysblock", "com.self.sysblock.AdminReceiver")
                                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, cn)
                                        putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Protects SysBlock from being removed.")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    // THIS WILL TELL US THE ERROR
                                    android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                    e.printStackTrace()
                                }
                            }
                        }
                    ) {
                        Text(
                            text = if (isAdminActive) "✅ Uninstall Protection Active" else "❌ Enable Uninstall Protection",
                            color = if (isAdminActive) Color.Green else Color.Red,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

            }
        }

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
                    onCheckedChange = { toggleMasterSwitch(it) },
                    enabled = isAccessibilityEnabled && isUsageEnabled && !isMasterSwitchFrozen,
                    colors = SwitchDefaults.colors(
                        disabledCheckedTrackColor = Color(0xFF004400),
                        disabledCheckedThumbColor = Color.Gray
                    )
                )
            }
        }

        Text("Active Rules", color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
        
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(parsedConfig.rules) { rule ->
                val appLabel = remember(rule.packageName) {
                    try {
                        val pm = context.packageManager
                        val info = pm.getApplicationInfo(rule.packageName, 0)
                        info.loadLabel(pm).toString()
                    } catch (e: Exception) {
                        rule.packageName
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(Color(0xFF1E1E1E))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = appLabel, 
                            color = Color.White, 
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = rule.packageName, 
                            color = Color.DarkGray, 
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    
                    Text(
                        if (rule.limitMinutes > 0) "${rule.limitMinutes}m LIMIT" else "INSTANT", 
                        color = if (rule.strictMode) Color.Red else Color.Yellow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Button(
            onClick = onNavigateToEditor,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text("OPEN CONFIG EDITOR", color = Color.Green)
        }
    }

    if (showHelpDialog) {
        HelpDialog(onDismiss = { showHelpDialog = false })
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
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "SYSTEM MANUAL", 
                    color = Color.Green, 
                    fontSize = 20.sp, 
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("1. Configuration", color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    "Rules are set in CONFIG.SYS using the format:\nPackage | Limit(m) | StrictMode\n\nTo Prevent Uninstall, add line:\nPREVENT_UNINSTALL",
                    color = Color.Gray, fontSize = 14.sp
                )
                
                Spacer(modifier = Modifier.height(12.dp))

                Text("2. Strict Mode", color = Color.Red, fontWeight = FontWeight.Bold)
                Text(
                    "If set to TRUE, the app will be blocked immediately when you exceed your daily minute limit.",
                    color = Color.Gray, fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("3. The Penalty System", color = Color(0xFFFF8800), fontWeight = FontWeight.Bold)
                Text(
                    "To reduce phone addiction, SysBlock punishes rapid reopening of apps.",
                    color = Color.Gray, fontSize = 14.sp
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
                ) {
                    Text("ACKNOWLEDGE")
                }
            }
        }
    }
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expectedComponentName = ComponentName(context, BlockerService::class.java)
    val enabledServicesSetting = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    val colonSplitter = SimpleStringSplitter(':')
    colonSplitter.setString(enabledServicesSetting)
    while (colonSplitter.hasNext()) {
        val componentNameString = colonSplitter.next()
        val enabledComponent = ComponentName.unflattenFromString(componentNameString)
        if (enabledComponent != null && enabledComponent == expectedComponentName) return true
    }
    return false
}

fun isUsageAccessGranted(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        context.packageName
    )
    return mode == AppOpsManager.MODE_ALLOWED
}
