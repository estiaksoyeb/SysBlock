package com.self.sysblock.ui.blocking

import android.content.Context
import com.self.sysblock.data.config.ConfigParser
import com.self.sysblock.features.usage.UsageManager

// Helper function that combines Usage Stats (from UsageManager) with Limits (from Config)
fun getUsageInfo(context: Context, pkgName: String): Pair<Int, Int> {
    val prefs = context.getSharedPreferences("SysBlockPrefs", Context.MODE_PRIVATE)
    val rawConfig = prefs.getString("raw_config", ConfigParser.getDefaultConfig()) ?: ""
    val config = ConfigParser.parse(rawConfig)
    val rule = config.rules.find { it.packageName == pkgName }
    val limit = rule?.limitMinutes ?: 0

    // Use the reusable UsageManager for accuracy
    val used = UsageManager.getDailyUsage(context, pkgName)

    return Pair(used, limit)
}