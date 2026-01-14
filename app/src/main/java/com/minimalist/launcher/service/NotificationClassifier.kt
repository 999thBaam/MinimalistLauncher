package com.minimalist.launcher.service

import android.content.Context
import android.service.notification.StatusBarNotification
import android.app.Notification
import android.util.Log

/**
 * NotificationClassifier: 2-Box Attention Routing
 * 
 * PRODUCT INVARIANTS (DO NOT CHANGE):
 * 1. Intent Firewall must NEVER delete, suppress, or cancel notifications.
 * 2. All notifications exist. Only attention is regulated.
 * 3. Unknown defaults to IMPORTANT (Silent) - Fail-Open.
 * 
 * BOX 1: IMPORTANT
 * - Calls, OTPs, VIPs, DMs, Mentions.
 * - Urgent: Ring + Vibrate + Pulse.
 * - Not Urgent: Silent + Steady Glow.
 * 
 * BOX 2: UNIMPORTANT
 * - Promos, Social Feeds, Spam.
 * - Silent delivery. No vibration. No glow.
 * - Stored in system tray AND right box.
 */
object NotificationClassifier {
    
    private const val TAG = "AttentionRouter"
    
    /**
     * Classification result: which box and urgency level.
     */
    data class Classification(
        val isImportant: Boolean,
        val isUrgent: Boolean,  // Only applies if isImportant=true
        val reason: String,     // For debugging/learning
        val isMessaging: Boolean = false // True for Chat apps, False for generic notifs
    )
    
    // ════════════════════════════════════════════════════════════════════════
    // Package Rules
    // ════════════════════════════════════════════════════════════════════════
    
    // Always IMPORTANT + URGENT
    private val URGENT_PACKAGES = setOf(
        "com.google.android.dialer",
        "com.android.phone",
        "com.android.mms"
    )
    
    // KNOWN MESSAGING APPS (Optimization)
    // We also detect via MessagingStyle, but these are sure hits.
    private val MESSAGING_PACKAGES = setOf(
        "com.whatsapp",
        "org.telegram.messenger",
        "com.google.android.apps.messaging",
        "com.android.messaging",
        "com.facebook.orca", // Messenger
        "com.discord",
        "com.slack",
        "org.thoughtcrime.securesms" // Signal
    )
    
    // Usually UNIMPORTANT
    private val SOCIAL_PACKAGES = setOf(
        "com.instagram.android",
        "com.facebook.katana",
        "com.twitter.android",
        "com.zhiliaoapp.musically", // TikTok
        "com.snapchat.android",
        "com.pinterest",
        "com.linkedin.android",
        "com.google.android.youtube",
        "com.spotify.music",
        "com.netflix.mediaclient"
    )
    
    /**
     * Classify a notification.
     * 
     * FAIL-OPEN: Any exception → IMPORTANT + URGENT (trust > cleverness)
     */
    fun classify(sbn: StatusBarNotification, context: Context): Classification {
        return try {
            classifyInternal(sbn, context)
        } catch (e: Exception) {
            Log.e(TAG, "Classification failed, fail-open to IMPORTANT+URGENT", e)
            Classification(isImportant = true, isUrgent = true, reason = "error_fallback", isMessaging = false)
        }
    }
    
    private fun classifyInternal(sbn: StatusBarNotification, context: Context): Classification {
        val pkg = sbn.packageName
        val category = sbn.notification.category
        
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // LAYER 0: SANITY FILTERS (System Noise & Ecosystem Priority)
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        
        // 1. System Noise (Displaying over other apps, etc.) -> KILL IT (Unimportant)
        if (isSystemNoise(sbn)) {
            return Classification(false, false, "system_noise", false)
        }
        
        // 2. Ecosystem Apps (Pause) -> HIGHEST PRIORITY (Important + Urgent)
        if (isEcosystemApp(sbn)) {
            return Classification(true, true, "ecosystem_priority", true)
        }
        
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // LAYER 1: BREAKTHROUGH PROTOCOL (IMPORTANT + URGENT)
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        val isMessaging = isMessagingApp(sbn)

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // LAYER 1: BREAKTHROUGH PROTOCOL (IMPORTANT + URGENT)
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        val breakthrough = BreakthroughDetector.checkBreakthrough(sbn)
        if (breakthrough == BreakthroughDetector.BreakthroughResult.IMMEDIATE) {
            return Classification(true, true, "breakthrough_immediate", isMessaging)
        }
        
        // Keyword Alone: GUARANTEE Important, but NEVER Urgent
        if (breakthrough == BreakthroughDetector.BreakthroughResult.ELEVATED) {
             return Classification(true, false, "breakthrough_elevated", isMessaging)
        }
        
        // Calls are IMPORTANT + URGENT
        if (category == Notification.CATEGORY_CALL) {
             return Classification(true, true, "call", isMessaging)
        }
        
        // OTP/Verification are IMPORTANT + URGENT
        if (isOtp(sbn)) {
             return Classification(true, true, "otp", isMessaging)
        }
        
        // System-critical packages
        if (pkg in URGENT_PACKAGES) {
             return Classification(true, true, "urgent_package", isMessaging)
        }
        
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // LAYER 2: VIP DETECTION (IMPORTANT, may be urgent)
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        if (VipDetector.isVip(sbn, context)) {
            // VIPs are Important. They are not Urgent unless they triggered IMMEDIATE breakthrough above.
            return Classification(true, false, "vip", isMessaging)
        }
        
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // LAYER 3: MESSAGING APPS (IMPORTANT)
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        if (isMessagingApp(sbn)) {
            return classifyMessaging(sbn, context)
        }
        
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // LAYER 4: SOCIAL / OTHER (UNIMPORTANT)
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        if (pkg in SOCIAL_PACKAGES) {
            // Mentions in social apps (e.g. "mentioned you in a comment") should permeate?
             // Let's check mentions even for social apps
             if (containsMention(sbn)) {
                 return Classification(true, false, "social_mention", isMessaging)
             }
            return Classification(false, false, "social", isMessaging)
        }
        
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // LAYER 5: CATEGORY FALLBACK
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        return classifyByCategory(sbn, isMessaging)
    }
    
    private fun isMessagingApp(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName in MESSAGING_PACKAGES) return true
        
        val notification = sbn.notification
        // Check Metadata Style (API 24+)
        val isMessagingStyle = notification.extras.getString(Notification.EXTRA_TEMPLATE) == "android.app.Notification\$MessagingStyle"
        if (isMessagingStyle) return true
        
        // Check Category
        if (notification.category == Notification.CATEGORY_MESSAGE) return true
        
        return false
    }
    
    private fun classifyMessaging(sbn: StatusBarNotification, context: Context): Classification {
        val extras = sbn.notification.extras
        val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        
        val isGroup = conversationTitle != null
        
        // Check for Mentions (Universal rule for messaging)
        if (containsMention(sbn)) {
            return Classification(true, false, "mention", true) // Messaging is true here
        }
        
        if (isGroup) {
            // Group:
            // 1. VIP Speaker? (Already checked in Layer 2 - effectively handled)
            // 2. Authority? (Checked below)
            
            if (VipDetector.hasGroupAuthority(sbn, context)) {
                return Classification(true, false, "group_authority", true)
            }
            
            // Default Group Messages to IMPORTANT (SILENT)
            // "Silence reduces noise; invisibility breaks trust."
            return Classification(true, false, "group_message_default", true)
        } else {
            // DM = IMPORTANT (not urgent)
            return Classification(true, false, "dm", true)
        }
    }
    
    private fun classifyByCategory(sbn: StatusBarNotification, isMessaging: Boolean): Classification {
        val category = sbn.notification.category
        
        // IMPORTANT categories
        if (category in listOf(
            Notification.CATEGORY_CALL,
            Notification.CATEGORY_ALARM,
            Notification.CATEGORY_MESSAGE,
            Notification.CATEGORY_EVENT,
            Notification.CATEGORY_REMINDER,
            Notification.CATEGORY_ERROR
        )) {
            val isUrgent = category in listOf(
                Notification.CATEGORY_CALL,
                Notification.CATEGORY_ALARM,
                Notification.CATEGORY_ERROR
            )
            return Classification(true, isUrgent, "category_important", isMessaging)
        }
        
        // UNIMPORTANT categories
        if (category in listOf(
            Notification.CATEGORY_PROMO,
            Notification.CATEGORY_RECOMMENDATION,
            Notification.CATEGORY_SOCIAL,
            Notification.CATEGORY_STATUS,
            Notification.CATEGORY_SERVICE
        )) {
            return Classification(false, false, "category_unimportant", isMessaging)
        }
        
        // Unknown = IMPORTANT (Safe Default / Fail-Open)
        return Classification(true, false, "unknown_safe_default", isMessaging)
    }
    
    private fun isOtp(sbn: StatusBarNotification): Boolean {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val content = "$title $text".lowercase()
        
        val otpKeywords = listOf("code", "otp", "pin", "password", "verification", "login", "2fa")
        return otpKeywords.any { content.contains(it) }
    }
    
    private fun containsMention(sbn: StatusBarNotification): Boolean {
        // 1. Regex Detection in Text
        val extras = sbn.notification.extras
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val combined = "$title $text"
        
        // Regex: At symbol preceded by start-of-line or whitespace, followed by 2+ alphanumerics
        val mentionRegex = Regex("(?<=^|\\s)@[a-zA-Z0-9_]{2,}")
        if (mentionRegex.containsMatchIn(combined)) return true
        
        // 2. Metadata: EXTRA_PEOPLE_LIST
        // Contains list of people involved. If current user is in it? No, this lists senders usually.
        // But some apps put "me" there? Unreliable. 
        // Better signal: "Marked as high priority" by app?
        // Let's stick to text + explicit keywords.
        
        // 3. Explicit keywords
        if (combined.lowercase().contains("mentioned you")) return true
        
        return false
    }
    

    private fun isSystemNoise(sbn: StatusBarNotification): Boolean {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val combined = "$title $text".lowercase()
        
        if (combined.contains("displaying over other apps")) return true
        if (combined.contains("running in the background")) return true
        if (combined.contains("usb debugging")) return true
        if (combined.contains("wireless debugging")) return true
        
        return false
    }

    private fun isEcosystemApp(sbn: StatusBarNotification): Boolean {
        // "Pause" is part of our ecosystem -> Highest Priority
        // Check for "pause" in package name (e.g. com.mindful.pause)
        if (sbn.packageName.contains("pause", ignoreCase = true)) return true
        
        return false
    }

}
