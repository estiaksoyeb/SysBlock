package com.self.sysblock.features.freeze

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
        val startLine: Int,
        val endLine: Int,
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

    fun getActiveFrozenRanges(context: Context): List<IntRange> {
        val rules = getRules(context)
        val managedIndices = mutableSetOf<Int>()
        val allowedIndices = mutableSetOf<Int>()
        
        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        for (rule in rules) {
            if (!rule.isEnabled) continue

            val s = (rule.startLine - 1).coerceAtLeast(0)
            val e = (rule.endLine - 1).coerceAtLeast(0)
            if (e < s) continue
            
            val range = s..e
            range.forEach { managedIndices.add(it) }

            val startMinutes = rule.startHour * 60 + rule.startMinute
            val endMinutes = rule.endHour * 60 + rule.endMinute
            
            val isNowAllowed = if (startMinutes < endMinutes) {
                currentMinutes in startMinutes until endMinutes
            } else {
                currentMinutes >= startMinutes || currentMinutes < endMinutes
            }

            if (isNowAllowed) {
                range.forEach { allowedIndices.add(it) }
            }
        }

        val frozenIndices = managedIndices - allowedIndices
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