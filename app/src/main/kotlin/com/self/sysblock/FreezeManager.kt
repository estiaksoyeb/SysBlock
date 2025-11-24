package com.self.sysblock

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Calendar

object FreezeManager {
    private const val PREFS_NAME = "SysBlockFreeze"
    private const val KEY_RULES = "rules_json"
    private val gson = Gson()

    data class FreezeRule(
        val id: Long = System.currentTimeMillis(),
        val startHour: Int,
        val startMinute: Int,
        val endHour: Int,
        val endMinute: Int,
        val startLine: Int, // 1-based
        val endLine: Int,   // 1-based
        val isEnabled: Boolean = true
    )

    fun getRules(context: Context): MutableList<FreezeRule> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_RULES, null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<FreezeRule>>() {}.type
        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun saveRules(context: Context, rules: List<FreezeRule>) {
        val json = gson.toJson(rules)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RULES, json)
            .apply()
    }

    // --- LOGIC INVERSION: WHITELIST MODE ---
    // Returns lines that are FROZEN (because they are managed but NOT in an allowed window)
    fun getActiveFrozenRanges(context: Context): List<IntRange> {
        val rules = getRules(context)
        
        // 1. Identify all lines that have a rule attached (Managed)
        val managedIndices = mutableSetOf<Int>()
        // 2. Identify all lines that are currently in an allowed time window
        val allowedIndices = mutableSetOf<Int>()
        
        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        for (rule in rules) {
            if (!rule.isEnabled) continue

            // Convert 1-based user input to 0-based index
            val s = (rule.startLine - 1).coerceAtLeast(0)
            val e = (rule.endLine - 1).coerceAtLeast(0)
            if (e < s) continue
            
            val range = s..e

            // Mark these lines as "Protected/Managed"
            range.forEach { managedIndices.add(it) }

            // Check if we are in the "Allowed Edit Time"
            val startMinutes = rule.startHour * 60 + rule.startMinute
            val endMinutes = rule.endHour * 60 + rule.endMinute
            
            val isNowAllowed = if (startMinutes < endMinutes) {
                currentMinutes in startMinutes until endMinutes
            } else {
                currentMinutes >= startMinutes || currentMinutes < endMinutes
            }

            // If time is allowed, mark these lines as authorized
            if (isNowAllowed) {
                range.forEach { allowedIndices.add(it) }
            }
        }

        // 3. Logic: If a line is Managed, it is Frozen UNLESS it is Allowed.
        val frozenIndices = managedIndices - allowedIndices

        // Convert indices back to Ranges for the Editor to consume
        val sortedFrozen = frozenIndices.toList().sorted()
        val activeRanges = mutableListOf<IntRange>()
        
        if (sortedFrozen.isNotEmpty()) {
            var rangeStart = sortedFrozen[0]
            var rangeEnd = sortedFrozen[0]
            
            for (i in 1 until sortedFrozen.size) {
                if (sortedFrozen[i] == rangeEnd + 1) {
                    rangeEnd = sortedFrozen[i]
                } else {
                    activeRanges.add(rangeStart..rangeEnd)
                    rangeStart = sortedFrozen[i]
                    rangeEnd = sortedFrozen[i]
                }
            }
            activeRanges.add(rangeStart..rangeEnd)
        }

        return activeRanges
    }
}
