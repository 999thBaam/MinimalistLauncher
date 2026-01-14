package com.minimalist.launcher.service

import android.app.Notification
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

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
    
    /**
     * Normalized sender identity to prevent cross-app confusion.
     * 
     * CRITICAL: Always include packageName to avoid mixing senders across apps.
     * - Same phone number from WhatsApp and SMS should be treated separately.
     */
    data class SenderKey(
        val packageName: String,
        val senderId: String  // Normalized: digits only for phone, lowercase for username
    ) {
        companion object {
            /**
             * Single utility function for sender normalization.
             * 
             * Rules:
             * - Phone numbers → digits only (E.164-like)
             * - Usernames → lowercase, trimmed
             * - Unknown → hash fallback (package + title)
             */
            fun fromNotification(sbn: StatusBarNotification): SenderKey {
                val pkg = sbn.packageName
                val extras = sbn.notification.extras
                
                // Safe extraction of notification extras
                // Many extras can be non-String types (Person, Parcelable, etc.)
                val title = safeGetString(extras, Notification.EXTRA_TITLE)
                val conversationTitle = safeGetString(extras, Notification.EXTRA_CONVERSATION_TITLE)
                
                // For messaging-style notifications, try to get sender info safely
                val senderPerson = safeGetSenderName(extras)
                
                // Attempt to identify sender
                val rawSender = when {
                    // WhatsApp group: title is group name, sender might be in text
                    conversationTitle.isNotBlank() -> conversationTitle
                    senderPerson != null -> senderPerson
                    title.isNotBlank() -> title
                    else -> "unknown"
                }
                
                val normalizedId = normalizeSenderId(rawSender)
                return SenderKey(pkg, normalizedId)
            }
            
            /**
             * Safely get a String from notification extras.
             * Handles cases where the value might be CharSequence or other types.
             */
            private fun safeGetString(extras: android.os.Bundle, key: String): String {
                return try {
                    extras.getCharSequence(key)?.toString() ?: ""
                } catch (e: Exception) {
                    ""
                }
            }
            
            /**
             * Safely extract sender name from messaging-style notifications.
             * The "android.messagingUser" extra is a Person object, not a String.
             */
            private fun safeGetSenderName(extras: android.os.Bundle): String? {
                return try {
                    // Try to get Person object (API 28+)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        val person = extras.getParcelable<android.app.Person>("android.messagingUser")
                        person?.name?.toString()
                    } else {
                        null
                    } ?: extras.getCharSequence("android.selfDisplayName")?.toString()
                } catch (e: Exception) {
                    // Silently fail - this is optional data
                    null
                }
            }
            
            /**
             * Normalize sender ID consistently:
             * - Phone numbers: extract digits only
             * - Usernames/names: lowercase, trimmed
             */
            private fun normalizeSenderId(raw: String): String {
                val trimmed = raw.trim()
                
                // Check if it looks like a phone number (contains mostly digits)
                val digitsOnly = trimmed.filter { it.isDigit() }
                if (digitsOnly.length >= 7 && digitsOnly.length <= 15) {
                    // Looks like a phone number
                    return digitsOnly
                }
                
                // Otherwise treat as username/name
                return trimmed.lowercase()
            }
        }
    }
    
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
        val senderKey = SenderKey.fromNotification(sbn)
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
