package com.minimalist.launcher.data.local

import android.content.Context
import android.util.Log
import com.minimalist.launcher.domain.model.IntentType
import java.util.Locale

/**
 * IntentCache: Aggressive Caching Layer.
 * 
 * Policy: "Write Once, Read Forever".
 * Purpose: Minimize LLM inference calls for standardized notifications.
 */
object IntentCache {

    private const val TAG = "IntentCache"
    private const val PREFS_NAME = "intent_cache_v1"

    /**
     * Cache Lookup.
     * Returns null if not found (Cache Miss).
     */
    fun get(context: Context, appPackage: String, title: String): IntentType? {
        val key = generateKey(appPackage, title)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        if (!prefs.contains(key)) return null
        
        val typeName = prefs.getString(key, null) ?: return null
        return try {
            IntentType.valueOf(typeName)
        } catch (e: Exception) {
            Log.e(TAG, "Invalid cached type: $typeName", e)
            null
        }
    }

    /**
     * Cache Write.
     * Persists the result forever.
     */
    fun put(context: Context, appPackage: String, title: String, type: IntentType) {
        val key = generateKey(appPackage, title)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        prefs.edit()
            .putString(key, type.name)
            .apply()
            
        Log.d(TAG, "Cached: $key -> $type")
    }

    /**
     * Generates a normalized cache key.
     * Normalization Rules:
     * 1. Lowercase
     * 2. Remove emojis
     * 3. Remove numbers, dates, percentages (variable data)
     * 4. Collapse whitespace
     */
    private fun generateKey(appPackage: String, title: String): String {
        return "$appPackage::${normalize(title)}"
    }

    private fun normalize(text: String): String {
        // 1. Lowercase
        var normalized = text.lowercase(Locale.ROOT)
        
        // 2 & 3. Remove numbers, percentages, dates, special chars
        // Regex: Replace anything that's NOT a letter or whitespace with empty space
        // This effectively removes numbers (0-9), punctuation, emojis, symbols
        normalized = normalized.replace(Regex("[^a-z\\s]"), " ")
        
        // 4. Collapse whitespace
        normalized = normalized.replace(Regex("\\s+"), " ").trim()
        
        return normalized
    }
}
