package com.minimalist.launcher.service

import android.app.Notification
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.concurrent.ConcurrentHashMap // Ensuring this import is present if not already
import com.minimalist.launcher.service.SenderResolver.SenderKey // Importing the new SenderKey

/**
 * BreakthroughDetector: The Safety Valve
 * 
 * Guarantees that emergencies ALWAYS break through, regardless of other rules.
 * This is the FIRST layer in the Intent Firewall decision stack.
 * 
 * INVARIANT: Always ON, non-configurable by default.
 * 
 * Detection Rules:
 * 1. Double-Knock: 3+ msgs OR 2+ calls within 2 min → IMMEDIATE
 * 2. Keywords: "urgent" etc + repeat → IMMEDIATE, alone → ELEVATED
 * 
 * Memory Safety: Timestamps older than 3 min are pruned aggressively.
 */
object BreakthroughDetector {
    
    private const val TAG = "BreakthroughDetector"
    
    // Sliding window for sender activity: SenderKey → List of timestamps
    private val senderTimestamps = ConcurrentHashMap<SenderKey, MutableList<Long>>()
    
    // Configuration
    private const val WINDOW_MS = 2 * 60 * 1000L  // 2 minutes
    private const val PRUNE_THRESHOLD_MS = 3 * 60 * 1000L  // 3 minutes (memory safety)
    private const val MESSAGE_BURST_THRESHOLD = 3
    private const val CALL_BURST_THRESHOLD = 2
    
    // Urgent keywords (lowercase for matching)
    private val URGENT_KEYWORDS = setOf(
        "urgent", "emergency", "pick up", "pickup",
        "delivery", "otp", "911", "help", "asap",
        "important", "call me", "call back"
    )
    
    // NOTE: SenderKey logic moved to SenderResolver

    
    /**
     * Check if this notification triggers a breakthrough.
     * 
     * EXECUTION ORDER (critical):
     * 1. Normalize sender
     * 2. Record event & update window
     * 3. Prune old timestamps
     * 4. Evaluate intent (double-knock, then keyword)
     * 
     * @return BreakthroughResult indicating priority level
     */
    fun checkBreakthrough(sbn: StatusBarNotification): BreakthroughResult {
        val senderKey = SenderResolver.resolve(sbn)
        val now = System.currentTimeMillis()
        val isCall = sbn.notification.category == Notification.CATEGORY_CALL
        
        // Step 1: Record this event
        recordEvent(senderKey, now)
        
        // Step 2: Prune old timestamps (memory safety)
        pruneOldTimestamps(now)
        
        // Step 3: Check double-knock (burst detection)
        val timestamps = senderTimestamps[senderKey] ?: emptyList()
        val recentCount = timestamps.count { it > now - WINDOW_MS }
        
        val threshold = if (isCall) CALL_BURST_THRESHOLD else MESSAGE_BURST_THRESHOLD
        if (recentCount >= threshold) {
            Log.d(TAG, "BREAKTHROUGH: Double-knock from $senderKey ($recentCount events)")
            return BreakthroughResult.IMMEDIATE
        }
        
        // Step 4: Check keywords
        val hasKeyword = checkKeywords(sbn)
        val isRepeatSender = recentCount >= 2  // At least 2 messages = repeat
        
        if (hasKeyword) {
            return if (isRepeatSender) {
                Log.d(TAG, "BREAKTHROUGH: Keyword + repeat from $senderKey")
                BreakthroughResult.IMMEDIATE
            } else {
                Log.d(TAG, "ELEVATED: Keyword alone from $senderKey")
                BreakthroughResult.ELEVATED
            }
        }
        
        return BreakthroughResult.NONE
    }
    
    /**
     * Convenience method for legacy compatibility.
     * Returns true only for IMMEDIATE breakthroughs.
     */
    fun isBreakthrough(sbn: StatusBarNotification): Boolean {
        return checkBreakthrough(sbn) == BreakthroughResult.IMMEDIATE
    }
    
    private fun recordEvent(senderKey: SenderKey, timestamp: Long) {
        val list = senderTimestamps.getOrPut(senderKey) { mutableListOf() }
        list.add(timestamp)
    }
    
    /**
     * Memory safety: Prune timestamps older than 3 minutes.
     * Remove sender entries entirely if list becomes empty.
     */
    private fun pruneOldTimestamps(now: Long) {
        val cutoff = now - PRUNE_THRESHOLD_MS
        
        val keysToRemove = mutableListOf<SenderKey>()
        
        senderTimestamps.forEach { (key, timestamps) ->
            timestamps.removeAll { it < cutoff }
            if (timestamps.isEmpty()) {
                keysToRemove.add(key)
            }
        }
        
        keysToRemove.forEach { senderTimestamps.remove(it) }
    }
    
    private fun checkKeywords(sbn: StatusBarNotification): Boolean {
        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getString(Notification.EXTRA_TEXT) ?: ""
        val content = "$title $text".lowercase()
        
        return URGENT_KEYWORDS.any { keyword -> content.contains(keyword) }
    }
    
    /**
     * Clear all tracking data. Useful for testing.
     */
    fun reset() {
        senderTimestamps.clear()
    }
    
    /**
     * Result of breakthrough detection.
     */
    enum class BreakthroughResult {
        IMMEDIATE,  // Deliver immediately (double-knock or keyword+repeat)
        ELEVATED,   // Soft interrupt (keyword alone)
        NONE        // No breakthrough detected
    }
}
