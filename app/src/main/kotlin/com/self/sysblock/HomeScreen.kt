package com.self.sysblock

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import android.text.TextUtils.SimpleStringSplitter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
fun HomeScreen(onNavigateToEditor: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = context.getSharedPreferences("SysBlockPrefs", Context.MODE_PRIVATE)

    var isAccessibilityEnabled by remember { mutableStateOf(false) }
    var isUsageEnabled by remember { mutableStateOf(false) } // NEW STATE
    var rawConfig by remember { mutableStateOf("") }
    var parsedConfig by remember { mutableStateOf(ConfigParser.SystemConfig()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = isAccessibilityServiceEnabled(context)
                isUsageEnabled = isUsageAccessGranted(context) // NEW CHECK
                rawConfig = prefs.getString("raw_config", ConfigParser.getDefaultConfig()) ?: ""
                parsedConfig = ConfigParser.parse(rawConfig)
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp)
    ) {
        Text(
            text = "SYSBLOCK // HOME",
            color = Color.Green,
            fontSize = 24.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // --- PERMISSIONS CARD ---
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("System Permissions", color = Color.Gray, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                
                // 1. Accessibility Check
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

                // 2. Usage Access Check (NEW)
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
            }
        }

        // --- MASTER CONTROL ---
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Master Switch", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (isAccessibilityEnabled && isUsageEnabled) "System Ready" else "Permissions Missing",
                        color = Color.Gray, 
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = parsedConfig.masterSwitch,
                    onCheckedChange = { toggleMasterSwitch(it) },
                    enabled = isAccessibilityEnabled && isUsageEnabled
                )
            }
        }

        Text("Active Rules", color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
        
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(parsedConfig.rules) { rule ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(Color(0xFF1E1E1E))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(rule.packageName.takeLast(15), color = Color.White, fontFamily = FontFamily.Monospace)
                    Text(
                        if (rule.limitMinutes > 0) "${rule.limitMinutes}m LIMIT" else "INSTANT BLOCK", 
                        color = if (rule.strictMode) Color.Red else Color.Yellow,
                        fontSize = 12.sp
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

// NEW HELPER
fun isUsageAccessGranted(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        context.packageName
    )
    return mode == AppOpsManager.MODE_ALLOWED
}