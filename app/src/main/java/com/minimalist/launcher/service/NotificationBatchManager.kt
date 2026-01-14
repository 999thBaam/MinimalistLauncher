package com.minimalist.launcher.service

import android.content.Context
import android.service.notification.StatusBarNotification
import android.app.NotificationManager
import android.app.NotificationChannel
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.minimalist.launcher.R
import com.minimalist.launcher.MainActivity
import com.minimalist.launcher.data.ShadowMessage
import com.minimalist.launcher.data.MessageContent
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * NotificationBatchManager: Shadow Inbox Data Store
 * 
 * Manages batched notifications with conversation threading.
 * Provides API for Shadow Inbox UI to read messages without opening source apps.
 * 
 * GUARANTEE: Reading via getShadowMessages() does NOT trigger read receipts.
 */
object NotificationBatchManager {
    
    private const val TAG = "TwoBoxStore"
    
    // ════════════════════════════════════════════════════════════════════════
    // 2-Box Storage: Important & Unimportant threads
    // ════════════════════════════════════════════════════════════════════════
    
    // Box 1: IMPORTANT (VIP, DMs, calls)
    private val importantInbox = ConcurrentHashMap<String, ShadowMessage>()
    private var isUrgentFlag = false
    
    // Box 2: UNIMPORTANT (Everything else, stored)
    private val unimportantInbox = ConcurrentHashMap<String, ShadowMessage>()
    
    // Configuration
    private const val BATCH_INTERVAL_MS = 30 * 1000L // 30 seconds
    private var activeContext: java.lang.ref.WeakReference<Context>? = null
    
    /**
     * Add a notification to the appropriate box.
     */
    fun addNotification(context: Context, sbn: StatusBarNotification, isImportant: Boolean, isUrgent: Boolean) {
        val notif = sbn.notification
        val extras = notif.extras
        
        val title = extras.getString(Notification.EXTRA_TITLE) ?: "Notification"
        val text = extras.getString(Notification.EXTRA_TEXT) ?: ""
        val conversationTitle = extras.getString(Notification.EXTRA_CONVERSATION_TITLE)
        val packageName = sbn.packageName
        
        // Determine if group
        val isGroup = conversationTitle != null
        val groupName = conversationTitle
        
        // Generate stable conversation key
        val senderId = if (isGroup) null else title
        val groupId = if (isGroup) conversationTitle else null
        val conversationKey = ShadowMessage.generateConversationKey(
            packageName, senderId, groupId, isGroup
        )
        
        // Create message content
        val messageContent = MessageContent(
            text = if (text.isNotBlank()) text else title,
            timestamp = sbn.postTime,
            senderName = if (isGroup) title else null
        )
        
        // Select target box
        val targetBox = if (isImportant) importantInbox else unimportantInbox
        
        // Threading logic
        val existing = targetBox[conversationKey]
        if (existing != null) {
            existing.addMessage(messageContent)
            // Update timestamp
            targetBox[conversationKey] = existing.copy(lastTimestamp = sbn.postTime)
        } else {
            val newThread = ShadowMessage(
                conversationKey = conversationKey,
                packageName = packageName,
                sender = senderId,
                senderDisplayName = title,
                messages = mutableListOf(messageContent),
                lastTimestamp = sbn.postTime,
                isGroup = isGroup,
                groupName = groupName
            )
            targetBox[conversationKey] = newThread
        }
        
        // Update urgent flag if important
        if (isImportant && isUrgent) {
            isUrgentFlag = true
        }
        
        Log.d(TAG, "Stored in ${if(isImportant) "IMPORTANT" else "UNIMPORTANT"}: $conversationKey")
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // Public API (for UI)
    // ════════════════════════════════════════════════════════════════════════
    
    fun getImportantMessages(): List<ShadowMessage> {
        return importantInbox.values.sortedByDescending { it.lastTimestamp }.toList()
    }
    
    fun getUnimportantMessages(): List<ShadowMessage> {
        return unimportantInbox.values.sortedByDescending { it.lastTimestamp }.toList()
    }
    
    fun getImportantCount(): Int = importantInbox.values.sumOf { it.messages.size }
    fun getUnimportantCount(): Int = unimportantInbox.values.sumOf { it.messages.size }
    
    fun hasUrgent(): Boolean = isUrgentFlag
    
    fun clearImportant() {
        importantInbox.clear()
        isUrgentFlag = false
    }
    
    fun clearUnimportant() {
        unimportantInbox.clear()
    }
    
    /**
     * Clear a specific thread (swipe-to-dismiss).
     * checks both boxes.
     */
    fun clearThread(conversationKey: String) {
        if (importantInbox.remove(conversationKey) != null) {
            // If we removed from important, check if we need to update urgent flag
            isUrgentFlag = importantInbox.values.any { 
                // We'd store isUrgent in ShadowMessage? Start simple.
                // Re-scanning entire inbox is expensive? 
                // For now, let's just leave isUrgentFlag until next add or explicit clear.
                // Ideally ShadowMessage should carry urgent flag.
                // Assuming we don't carry it for now, let's just keep flag as is.
                false 
            }
        }
        unimportantInbox.remove(conversationKey)
        Log.d(TAG, "Cleared thread $conversationKey")
    }
    
    /**
     * Move a thread between boxes (Promote/Demote).
     */
    fun moveThread(conversationKey: String, toImportant: Boolean) {
        val sourceBox = if (toImportant) unimportantInbox else importantInbox
        val targetBox = if (toImportant) importantInbox else unimportantInbox
        
        val thread = sourceBox.remove(conversationKey)
        if (thread != null) {
            targetBox[conversationKey] = thread
            Log.d(TAG, "Moved thread $conversationKey to ${if(toImportant) "IMPORTANT" else "UNIMPORTANT"}")
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════════════════════════════
    
    // Cache for app labels
    private val appLabelCache = ConcurrentHashMap<String, String>()
    
    fun getAppName(packageName: String, context: Context? = null): String {
        return appLabelCache.getOrPut(packageName) {
            try {
                if (context != null) {
                    val pm = context.packageManager
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } else {
                    packageName.substringAfterLast(".")
                        .replaceFirstChar { it.uppercase() }
                        .take(15)
                }
            } catch (_: Exception) {
                packageName.substringAfterLast(".")
                    .replaceFirstChar { it.uppercase() }
            }
        }
    }
}
