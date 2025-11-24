package com.self.sysblock.data.config

data class AppRule(
    val packageName: String,
    val limitMinutes: Int, 
    val strictMode: Boolean
)

data class SystemConfig(
    val rules: List<AppRule> = emptyList(),
    val masterSwitch: Boolean = true,
    val preventUninstall: Boolean = false,
    // NEW: Configurable Session Times (Default: 5m, 10m, 20m, 30m)
    val sessionTimes: List<Int> = listOf(300, 600, 1200, 1800)
)