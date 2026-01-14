package com.minimalist.launcher.service

import android.content.Context
import android.service.notification.StatusBarNotification
import android.app.Notification
import android.util.Log

/**
 * NotificationClassifier: 2-Box Attention Routing
 * 
 * Classifies notifications into 2 boxes:
 * 
 * BOX 1: IMPORTANT
 * - VIP senders, DMs, calls, OTP, @mentions
 * - Urgent: ring + vibrate + glow
 * - Not urgent: just glow
 * 
 * BOX 2: UNIMPORTANT  
 * - Everything else (nothing dropped)
 * - User can promote to important
 * 
 * CRITICAL: Nothing is ever dropped. User always has access.
 */
object NotificationClassifier {
    
    private const val TAG = "AttentionRouter"
    
    /**
     * Classification result: which box and urgency level.
     */
    data class Classification(
        val isImportant: Boolean,
        val isUrgent: Boolean,  // Only applies if isImportant=true
        val reason: String      // For debugging/learning
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
    
    // IMPORTANT (may or may not be urgent)
    private val MESSAGING_PACKAGES = setOf(
        "com.whatsapp",
        "org.telegram.messenger",
        "com.google.android.apps.messaging",
        "com.android.messaging",
        "com.facebook.orca"
    )
    
    // Usually UNIMPORTANT
    private val SOCIAL_PACKAGES = setOf(
        "com.instagram.android",
        "com.facebook.katana",
        "com.twitter.android",
        "com.zhiliaoapp.musically",
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
            Classification(isImportant = true, isUrgent = true, reason = "error_fallback")
        }
    }
    
    private fun classifyInternal(sbn: StatusBarNotification, context: Context): Classification {
        val pkg = sbn.packageName
        val category = sbn.notification.category
        
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // LAYER 1: BREAKTHROUGH PROTOCOL (IMPORTANT + URGENT)
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        val breakthrough = BreakthroughDetector.checkBreakthrough(sbn)
        if (breakthrough == BreakthroughDetector.BreakthroughResult.IMMEDIATE) {
            return Classification(true, true, "breakthrough")
        }
        
        // Calls are IMPORTANT + URGENT
        if (category == Notification.CATEGORY_CALL) {
            return Classification(true, true, "call")
        }
        
        // OTP/Verification are IMPORTANT + URGENT
        if (isOtp(sbn)) {
            return Classification(true, true, "otp")
        }
        
        // System-critical packages
        if (pkg in URGENT_PACKAGES) {
            return Classification(true, true, "urgent_package")
        }
        
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // LAYER 2: VIP DETECTION (IMPORTANT, may be urgent)
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        if (VipDetector.isVip(sbn, context)) {
            val isUrgent = breakthrough == BreakthroughDetector.BreakthroughResult.ELEVATED
            return Classification(true, isUrgent, "vip")
        }
        
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // LAYER 3: MESSAGING APPS (IMPORTANT, not urgent by default)
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        if (pkg in MESSAGING_PACKAGES) {
            return classifyMessaging(sbn, context)
        }
        
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // LAYER 4: SOCIAL / OTHER (UNIMPORTANT)
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        if (pkg in SOCIAL_PACKAGES) {
            return Classification(false, false, "social")
        }
        
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // LAYER 5: CATEGORY FALLBACK
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        return classifyByCategory(sbn)
    }
    
    private fun classifyMessaging(sbn: StatusBarNotification, context: Context): Classification {
        val extras = sbn.notification.extras
        val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        
        val isGroup = conversationTitle != null
        
        if (isGroup) {
            // Group: @mention = IMPORTANT
            if (containsMention(text)) {
                return Classification(true, false, "group_mention")
            }
            if (VipDetector.hasGroupAuthority(sbn, context)) {
                return Classification(true, false, "group_authority")
            }
            // Regular group message = UNIMPORTANT
            return Classification(false, false, "group_message")
        } else {
            // DM = IMPORTANT (not urgent)
            return Classification(true, false, "dm")
        }
    }
    
    private fun classifyByCategory(sbn: StatusBarNotification): Classification {
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
            return Classification(true, isUrgent, "category_important")
        }
        
        // UNIMPORTANT categories
        if (category in listOf(
            Notification.CATEGORY_PROMO,
            Notification.CATEGORY_RECOMMENDATION,
            Notification.CATEGORY_SOCIAL,
            Notification.CATEGORY_STATUS,
            Notification.CATEGORY_SERVICE
        )) {
            return Classification(false, false, "category_unimportant")
        }
        
        // Unknown = UNIMPORTANT (user can promote)
        return Classification(false, false, "unknown")
    }
    
    private fun isOtp(sbn: StatusBarNotification): Boolean {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val content = "$title $text".lowercase()
        
        val otpKeywords = listOf("code", "otp", "pin", "password", "verification", "login", "2fa")
        return otpKeywords.any { content.contains(it) }
    }
    
    private fun containsMention(text: String): Boolean {
        return text.contains("@") || text.lowercase().contains("mentioned")
    }
    
    // Legacy compatibility
    @Deprecated("Use classify() with Classification")
    fun isImportant(sbn: StatusBarNotification): Boolean {
        val pkg = sbn.packageName
        if (pkg in URGENT_PACKAGES || pkg in MESSAGING_PACKAGES) return true
        if (pkg in SOCIAL_PACKAGES) return false
        return true
    }
}
