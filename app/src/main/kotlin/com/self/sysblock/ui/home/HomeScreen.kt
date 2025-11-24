package com.self.sysblock.ui.home

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.os.Process
import android.provider.Settings
import android.text.TextUtils.SimpleStringSplitter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.self.sysblock.data.config.ConfigParser
import com.self.sysblock.data.config.SystemConfig
import com.self.sysblock.features.freeze.FreezeManager
import com.self.sysblock.service.BlockerService

@Composable
fun HomeScreen(onNavigateToEditor: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = context.getSharedPreferences("SysBlockPrefs", Context.MODE_PRIVATE)

    var isAccessibilityEnabled by remember { mutableStateOf(false) }
    var isUsageEnabled by remember { mutableStateOf(false) }
    var isAdminActive by remember { mutableStateOf(false) }
    
    var rawConfig by remember { mutableStateOf("") }
    var parsedConfig by remember { mutableStateOf(SystemConfig()) }
    
    var activeFrozenRanges by remember { mutableStateOf(emptyList<IntRange>()) }

    // Check for Admin permission manually here to pass down status
    // Note: The actual Intent logic is inside SystemControlPanel now
    fun checkAdminStatus(): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        val componentName = ComponentName(context, com.self.sysblock.receivers.AdminReceiver::class.java)
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
        // 1. Top Panel (Header + Permissions + Master Switch)
        SystemControlPanel(
            context = context,
            isAccessibilityEnabled = isAccessibilityEnabled,
            isUsageEnabled = isUsageEnabled,
            isAdminActive = isAdminActive,
            parsedConfig = parsedConfig,
            isMasterSwitchFrozen = isMasterSwitchFrozen,
            onToggleMaster = { toggleMasterSwitch(it) }
        )

        // 2. Active Rules List (Takes up remaining space)
        ActiveRulesList(
            context = context,
            rules = parsedConfig.rules,
            modifier = Modifier.weight(1f).padding(top = 16.dp)
        )

        // 3. Config Button (Bottom)
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
    val enabledServicesSetting = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
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
    @Suppress("DEPRECATION")
    val mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    return mode == AppOpsManager.MODE_ALLOWED
}