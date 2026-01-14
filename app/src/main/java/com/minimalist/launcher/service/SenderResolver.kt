package com.minimalist.launcher.service

import android.app.Notification
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * SenderResolver: Centralized Identity Management
 * 
 * INVARIANT: Robust, Fail-Safe, Consistent.
 * 
 * Provides a single source of truth for "Who sent this?".
 * Prevents identity fracturing across different subsystems (VIP vs Breakthrough vs Storage).
 */
object SenderResolver {
    
    private const val TAG = "SenderResolver"
    
    /**
     * Normalized sender identity.
     */
    data class SenderKey(
        val packageName: String,
        val senderId: String // Normalized: digits only for phone, lowercase for username
    )
    
    /**
     * Extract a stable SenderKey from a notification.
     * 
     * FAIL-OPEN: Never throws. Returns a safe fallback if parsing fails.
     */
    fun resolve(sbn: StatusBarNotification): SenderKey {
        return try {
            resolveInternal(sbn)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve sender for ${sbn.packageName}", e)
            // Fallback: Use package name + title-ish hash to avoid collisions
            val fallbackId = "fallback_${sbn.packageName}_${System.currentTimeMillis()}"
            SenderKey(sbn.packageName, fallbackId)
        }
    }
    
    private fun resolveInternal(sbn: StatusBarNotification): SenderKey {
        val pkg = sbn.packageName
        val extras = sbn.notification.extras
        
        val title = safeGetString(extras, Notification.EXTRA_TITLE)
        val conversationTitle = safeGetString(extras, Notification.EXTRA_CONVERSATION_TITLE)
        
        // Try to get Person object (MessagingStyle)
        val senderPersonName = safeGetSenderName(extras)
        
        // Priority for Identity:
        // 1. Conversation Title (Group Name) - ONLY if it's a group message
        // 2. Sender Person Name (MessagingStyle)
        // 3. Notification Title (Traditional)
        
        val rawSender = when {
            // If it has a conversation title, it's likely a group. use that as the "Sender" for grouping purposes?
            // Wait, for VIP/Breakthrough we usually care about the *actual user* who sent it, not the group.
            // But for Threading (BatchManager), we care about the Conversation.
            
            // Let's stick to the previous Breakthrough logic which seemed to work:
            // Group Title > Sender Name > Title.
            // However, note: "SenderKey" is used for *VIP detection* too. 
            // If I am in a group "Family", and "Mom" sends a message:
            // - BatchManager wants to group by "Family"
            // - VipDetector wants to know it's "Mom"
            
            // This is a subtle distinction. 
            // The previous Breakthrough implementation used:
            // if (conversationTitle.isNotBlank()) -> conversationTitle
            
            // Let's refine for v2: 
            // We need a specific "Identity" for VIP checks. 
            // And a "ThreadKey" for storage.
            // This class provides the IDENTITY (the "Who").
            
            // For a group message "Family" from "Mom":
            // Identity = "Mom" (for VIP check)
            // Thread = "Family" (for UI grouping)
            
            // BUT, our current architecture keys everything off "SenderKey". 
            // Changing this requires a bigger refactor. 
            // Let's stick to the robust logic we had:
            // If it's a group, the "Sender" effectively becomes the Group for Breakthrough purposes?
            // Actually, if "Mom" spams 5 messages in "Family", we want Breakthrough.
            // If "Random Guy" spams 5 messages in "Big Group", we maybe don't?
            
            // Let's stick to "Person" > "Title" for IDENTITY if available.
            // If conversation title exists, it's a context, but not the sender.
            
            // REVISION: Previous logic:
            // conversationTitle.isNotBlank() -> conversationTitle
            
            // I will implement a slightly smarter resolution:
            // If we have a specific sender person, use that (it's more granular).
            // If not, fall back to titles.
            
            senderPersonName != null && senderPersonName.isNotBlank() -> senderPersonName
            title.isNotBlank() -> title
            conversationTitle.isNotBlank() -> conversationTitle // Fallback for some apps
            else -> "unknown"
        }
        
        val normalizedId = normalizeSenderId(rawSender)
        return SenderKey(pkg, normalizedId)
    }
    
    /**
     * Normalize sender ID consistently:
     * - Phone numbers: extract digits only
     * - Usernames/names: lowercase, trimmed
     */
    fun normalizeSenderId(raw: String): String {
        val trimmed = raw.trim()
        
        // Check if it looks like a phone number (contains mostly digits)
        // Heuristic: at least 7 digits, allowed chars are digits, space, +, -, (, )
        val digitsOnly = trimmed.filter { it.isDigit() }
        
        // Strict phone number check: 
        // If we strip non-digits and it looks like a phone number (7-15 digits), treat as such.
        // This prevents "User 123" from becoming "123".
        // But for contacts matching, strict digits is usually best.
        
        if (digitsOnly.length >= 7 && digitsOnly.length <= 15) {
             // Check if the original string was 'mostly' numbers to avoid "Agent 007" becoming "007"
             // (Simplified heuristic for now)
             return digitsOnly
        }
        
        // Otherwise treat as username/name
        return trimmed.lowercase()
    }
    
    private fun safeGetString(extras: android.os.Bundle, key: String): String {
        return try {
            extras.getCharSequence(key)?.toString() ?: ""
        } catch (e: Exception) {
            ""
        }
    }
    
    private fun safeGetSenderName(extras: android.os.Bundle): String? {
        return try {
            // Try to get Person object (API 28+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val person = extras.getParcelable<android.app.Person>("android.messagingUser")
                return person?.name?.toString()
            }
            // Fallback for older APIs or if Person is missing
            extras.getCharSequence("android.selfDisplayName")?.toString()
        } catch (e: Exception) {
            null
        }
    }
}
