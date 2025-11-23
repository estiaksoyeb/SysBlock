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
        val id: Long = System.currentTimeMillis(), // Simple ID
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

    // Returns a list of line INDICES (0-based) that are currently frozen
    fun getActiveFrozenRanges(context: Context): List<IntRange> {
        val rules = getRules(context)
        val activeRanges = mutableListOf<IntRange>()
        
        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        for (rule in rules) {
            if (!rule.isEnabled) continue

            val startMinutes = rule.startHour * 60 + rule.startMinute
            val endMinutes = rule.endHour * 60 + rule.endMinute
            
            val isActive = if (startMinutes < endMinutes) {
                // Normal range (e.g., 08:00 to 23:00)
                currentMinutes in startMinutes until endMinutes
            } else {
                // Overnight range (e.g., 22:00 to 06:00)
                currentMinutes >= startMinutes || currentMinutes < endMinutes
            }

            if (isActive) {
                // Convert 1-based user input to 0-based index
                // Ensure we don't have negative ranges
                val s = (rule.startLine - 1).coerceAtLeast(0)
                val e = (rule.endLine - 1).coerceAtLeast(0)
                if (e >= s) {
                    activeRanges.add(s..e)
                }
            }
        }
        return activeRanges
    }
}