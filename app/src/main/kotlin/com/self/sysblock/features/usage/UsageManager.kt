package com.self.sysblock.features.usage

import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar

object UsageManager {

    fun getDailyUsage(context: Context, pkgName: String): Int {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = now
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis

        // Use AGGREGATE for accuracy
        val statsMap = usageStatsManager.queryAndAggregateUsageStats(startTime, now)
        val usage = statsMap[pkgName]
        
        return ((usage?.totalTimeInForeground ?: 0) / 1000 / 60).toInt()
    }
}
