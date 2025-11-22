package com.self.sysblock

import android.content.Context

object PenaltyManager {

    private const val PREFS_NAME = "SysBlockPenalty"
    private const val GAP_TOLERANCE_MS = 2 * 60 * 1000 // 2 Minutes
    
    data class Status(
        val strikeCount: Int,
        val isPenaltyNext: Boolean,
        val lockoutEndTime: Long
    )

    fun getStatus(context: Context, pkg: String): Status {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val strikeCount = prefs.getInt("${pkg}_strikes", 0)
        val lockoutEnd = prefs.getLong("${pkg}_lockout_end", 0L)
        val lastSessionEnd = prefs.getLong("${pkg}_last_end", 0L)
        
        val now = System.currentTimeMillis()
        
        // --- PREDICTIVE LOGIC FOR UI ---
        
        // 1. Check if we just finished a penalty
        // If there was a lockout, and we passed it, we are clean.
        if (lockoutEnd > 0 && now > lockoutEnd) {
            return Status(0, false, lockoutEnd)
        }

        // 2. Check Time Gap (Cool-down)
        // If we waited longer than 2 mins, we are clean.
        if (now - lastSessionEnd > GAP_TOLERANCE_MS) {
             return Status(0, false, lockoutEnd)
        }

        // 3. Otherwise, we are still in the danger zone.
        // If current strikes >= 2, the NEXT one will match 3 and trigger penalty.
        return Status(strikeCount, strikeCount >= 2, lockoutEnd)
    }

    fun isLockedOut(context: Context, pkg: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lockoutEnd = prefs.getLong("${pkg}_lockout_end", 0L)
        val lastSessionEnd = prefs.getLong("${pkg}_last_end", 0L)
        
        val now = System.currentTimeMillis()
        
        // Locked out only if Session ended AND Lockout hasn't expired
        return (now > lastSessionEnd) && (now < lockoutEnd)
    }

    fun startSession(context: Context, pkg: String, durationSeconds: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val durationMs = durationSeconds * 1000L

        val lastSessionEnd = prefs.getLong("${pkg}_last_end", 0L)
        val lockoutEnd = prefs.getLong("${pkg}_lockout_end", 0L)
        var strikes = prefs.getInt("${pkg}_strikes", 0)

        // --- CALCULATE STRIKES ---
        
        var shouldReset = false

        // Rule A: Did they just serve a penalty?
        if (lockoutEnd > 0 && now > lockoutEnd) {
            shouldReset = true
        }
        // Rule B: Did they wait long enough (2 mins)?
        else if (now - lastSessionEnd > GAP_TOLERANCE_MS) {
            shouldReset = true
        }

        if (shouldReset) {
            strikes = 1 // New Fresh Start
        } else {
            strikes++ // Rapid Re-entry -> Strike +1
        }

        val editor = prefs.edit()
        
        val thisSessionEnd = now + durationMs
        editor.putLong("${pkg}_last_end", thisSessionEnd)
        editor.putInt("${pkg}_strikes", strikes)

        // --- APPLY PENALTY ---
        if (strikes >= 3) {
            val penaltyDuration = durationMs * 2 
            val newLockoutEnd = thisSessionEnd + penaltyDuration
            editor.putLong("${pkg}_lockout_end", newLockoutEnd)
        } else {
            // Clear any old lockout data if we reset
            if (shouldReset) {
                editor.putLong("${pkg}_lockout_end", 0L)
            }
        }

        // Save Session Ticket
        val sessionPrefs = context.getSharedPreferences("SysBlockSessions", Context.MODE_PRIVATE)
        sessionPrefs.edit().putLong(pkg, thisSessionEnd).apply()

        editor.apply()
    }
}