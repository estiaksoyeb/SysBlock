package com.self.sysblock

import android.content.Context
import java.util.Calendar

object PenaltyManager {

    private const val PREFS_NAME = "SysBlockPenalty"
    private const val GAP_TOLERANCE_MS = 2 * 60 * 1000 // 2 Minutes
    
    data class Status(
        val strikeCount: Int,
        val isPenaltyNext: Boolean,
        val lockoutEndTime: Long,
        val nextMultiplier: Int // For UI: "3x Penalty"
    )

    // Helper to check if it's a new day and reset counters
    private fun checkDayReset(context: Context, pkg: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastDay = prefs.getInt("${pkg}_last_day", -1)
        val currentDay = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        
        if (currentDay != lastDay) {
            // NEW DAY: Reset everything
            prefs.edit()
                .putInt("${pkg}_last_day", currentDay)
                .putInt("${pkg}_daily_penalty_level", 1) // Start at Level 1
                .putInt("${pkg}_strikes", 0)
                .putLong("${pkg}_lockout_end", 0L)
                .apply()
        }
    }

    fun getStatus(context: Context, pkg: String): Status {
        checkDayReset(context, pkg) // Ensure data is fresh for today
        
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        val strikeCount = prefs.getInt("${pkg}_strikes", 0)
        val lockoutEnd = prefs.getLong("${pkg}_lockout_end", 0L)
        val lastSessionEnd = prefs.getLong("${pkg}_last_end", 0L)
        val currentLevel = prefs.getInt("${pkg}_daily_penalty_level", 1)
        
        val now = System.currentTimeMillis()
        
        // --- PREDICTIVE LOGIC ---
        
        // 1. Reset Logic (Lockout served OR Cool-down passed)
        if ((lockoutEnd > 0 && now > lockoutEnd) || (now - lastSessionEnd > GAP_TOLERANCE_MS)) {
             // If we start now, it will be Strike 1 (Safe)
             // Multiplier applies to the FUTURE penalty, which is currentLevel + 1
             return Status(0, false, lockoutEnd, currentLevel + 1)
        }

        // 2. Danger Zone
        // If strikes >= 2, next one is penalty.
        // The multiplier used will be (currentLevel + 1)
        return Status(strikeCount, strikeCount >= 2, lockoutEnd, currentLevel + 1)
    }

    fun isLockedOut(context: Context, pkg: String): Boolean {
        // Day reset isn't strictly needed here as timestamps fix themselves, 
        // but good for consistency.
        checkDayReset(context, pkg) 
        
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lockoutEnd = prefs.getLong("${pkg}_lockout_end", 0L)
        val lastSessionEnd = prefs.getLong("${pkg}_last_end", 0L)
        
        val now = System.currentTimeMillis()
        return (now > lastSessionEnd) && (now < lockoutEnd)
    }

    fun startSession(context: Context, pkg: String, durationSeconds: Int) {
        checkDayReset(context, pkg)
        
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val durationMs = durationSeconds * 1000L

        val lastSessionEnd = prefs.getLong("${pkg}_last_end", 0L)
        val lockoutEnd = prefs.getLong("${pkg}_lockout_end", 0L)
        var strikes = prefs.getInt("${pkg}_strikes", 0)
        var penaltyLevel = prefs.getInt("${pkg}_daily_penalty_level", 1)

        // --- STRIKE LOGIC ---
        var shouldReset = false
        if (lockoutEnd > 0 && now > lockoutEnd) shouldReset = true
        else if (now - lastSessionEnd > GAP_TOLERANCE_MS) shouldReset = true

        if (shouldReset) {
            strikes = 1 
        } else {
            strikes++ 
        }

        val editor = prefs.edit()
        
        val thisSessionEnd = now + durationMs
        editor.putLong("${pkg}_last_end", thisSessionEnd)
        editor.putInt("${pkg}_strikes", strikes)

        // --- PENALTY LOGIC ---
        if (strikes >= 3) {
            // FORMULA: Duration * (Level + 1)
            // Level 1: 10s * 2 = 20s
            // Level 2: 10s * 3 = 30s
            val multiplier = penaltyLevel + 1
            val penaltyDuration = durationMs * multiplier
            
            val newLockoutEnd = thisSessionEnd + penaltyDuration
            editor.putLong("${pkg}_lockout_end", newLockoutEnd)
            
            // INCREASE DIFFICULTY FOR NEXT TIME
            // "20+original... then 40s"
            penaltyLevel++
            editor.putInt("${pkg}_daily_penalty_level", penaltyLevel)
            
        } else {
            if (shouldReset) editor.putLong("${pkg}_lockout_end", 0L)
        }

        val sessionPrefs = context.getSharedPreferences("SysBlockSessions", Context.MODE_PRIVATE)
        sessionPrefs.edit().putLong(pkg, thisSessionEnd).apply()

        editor.apply()
    }
}