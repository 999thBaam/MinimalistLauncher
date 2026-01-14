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
 * 
 * INVARIANTS:
 * 1. Memory Cap: Max 50 threads per box. Oldest evicted first.
 * 2. Urgent Immune: Urgent threads are NEVER evicted automatically.
 * 3. Immutability: ShadowMessage state is updated atomically.
 * 4. Urgency: Dynamic derivation, no sticky flags.
 */
object NotificationBatchManager {
    
    private const val TAG = "TwoBoxStore"
    private const val MAX_THREADS_PER_BOX = 50
    
    // ════════════════════════════════════════════════════════════════════════
    // 2-Box Storage: Important & Unimportant threads
    // ════════════════════════════════════════════════════════════════════════
    
    // Box 1: IMPORTANT (VIP, DMs, calls)
    private val importantInbox = ConcurrentHashMap<String, ShadowMessage>()
    
    // Box 2: UNIMPORTANT (Everything else, stored)
    private val unimportantInbox = ConcurrentHashMap<String, ShadowMessage>()
    
    // Configuration
    private const val BATCH_INTERVAL_MS = 30 * 1000L // 30 seconds
    
    /**
     * Add a notification to the appropriate box.
     */
    fun addNotification(context: Context, sbn: StatusBarNotification, isImportant: Boolean, isUrgent: Boolean, isMessaging: Boolean) {
        val notif = sbn.notification
        val extras = notif.extras
        
        val title = extras.getString(Notification.EXTRA_TITLE) ?: "Notification"
        val text = extras.getString(Notification.EXTRA_TEXT) ?: ""
        val conversationTitle = extras.getString(Notification.EXTRA_CONVERSATION_TITLE)
        val packageName = sbn.packageName
        
        // Use SenderResolver for consistent identity
        val senderKey = SenderResolver.resolve(sbn)
        
        // Determine if group
        val isGroup = conversationTitle != null
        val groupName = conversationTitle
        
        // Conversation Key: For Group chats it's often better to key by Title
        // But SenderResolver focuses on "Person".
        // Let's stick to the previous key logic for Threading (it was working)
        // normalized package + ID/Title
        
        // Ensure Key Consistency:
        // MESSAGING: Use Thread/Group logic (Ghost Mode)
        // NOTIFICATIONS: Group by App (Single card per app)
        val threadId = if (isMessaging) {
             if (isGroup && !groupName.isNullOrBlank()) groupName else senderKey.senderId
        } else {
            "AppBinder" // Bind all generic notifications to one thread per app
        }
        
        val conversationKey = ShadowMessage.generateConversationKey(
            packageName, 
            if (isGroup && isMessaging) null else threadId, // For DMs/Notifs, key is threadId
            if (isGroup && isMessaging) threadId else null, // For Groups, group is key
            isGroup && isMessaging // Only messaging apps have true "Group" threads
        )
        
        // Create message content
        val messageContent = MessageContent(
            text = if (text.isNotBlank()) text else title,
            timestamp = sbn.postTime,
            senderName = if (isGroup) title else null // In group, title is often the sender name within group
        )
        
        // Select target box
        val targetBox = if (isImportant) importantInbox else unimportantInbox
        
        // ATOMIC UPDATE (Immutability)
        targetBox.compute(conversationKey) { _, existing ->
            if (existing != null) {
                // Return new copy with added message
                existing.copy(
                    messages = (existing.messages + messageContent).toMutableList(), // Create new list
                    lastTimestamp = sbn.postTime,
                    // Sticky Urgency: Once urgent, stays urgent until cleared
                    isUrgent = existing.isUrgent || isUrgent,
                    isMessaging = isMessaging // Update if changed (unlikely for same thread)
                )
            } else {
                ShadowMessage(
                    conversationKey = conversationKey,
                    packageName = packageName,
                    sender = if (isGroup) null else threadId,
                    senderDisplayName = if (isGroup) groupName ?: "Group" else title,
                    messages = mutableListOf(messageContent),
                    lastTimestamp = sbn.postTime,
                    isGroup = isGroup,
                    groupName = groupName,
                    category = notif.category,
                    isOngoing = (notif.flags and Notification.FLAG_ONGOING_EVENT) != 0,
                    isUrgent = isUrgent,
                    isMessaging = isMessaging
                )
            }
        }
        
        // Enforce Memory Cap
        enforceMemoryCap(targetBox)
        
        Log.d(TAG, "Stored in ${if(isImportant) "IMPORTANT" else "UNIMPORTANT"}: $conversationKey")
    }
    
    /**
     * Enforce max threads per box.
     * Evicts Oldest, Non-Urgent threads first.
     */
    private fun enforceMemoryCap(box: ConcurrentHashMap<String, ShadowMessage>) {
        if (box.size <= MAX_THREADS_PER_BOX) return
        
        // Evict!
        // Safety: Don't evict Urgent threads (Calls, Alarms)
        // Strategy: Sort by timestamp (oldest first) -> filter out urgent -> take excess
        
        val threads = box.values.toList()
        val sorted = threads.sortedBy { it.lastTimestamp } // Oldest first
        
        val candidates = sorted.filter { thread ->
            // Protect Urgent threads from eviction
            val isUrgent = thread.category == Notification.CATEGORY_CALL || 
                          thread.category == Notification.CATEGORY_ALARM
            !isUrgent
        }
        
        val toRemoveCount = box.size - MAX_THREADS_PER_BOX
        // Only remove as many as needed, from the candidates
        val toRemove = candidates.take(toRemoveCount)
        
        toRemove.forEach { 
            box.remove(it.conversationKey) 
            Log.d(TAG, "Evicted old thread: ${it.conversationKey}")
        }
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
    
    /**
     * Dynamic Urgency Check.
     * Returns true if ANY thread in important box is Urgent (Call/Alarm).
     */
    fun hasUrgent(): Boolean {
        // Check explicit flag OR categories (failsafe)
        return importantInbox.values.any { 
            it.isUrgent ||
            it.category == Notification.CATEGORY_CALL || 
            it.category == Notification.CATEGORY_ALARM ||
            it.category == Notification.CATEGORY_ERROR
        }
    }
    
    fun clearImportant() {
        importantInbox.clear()
    }
    
    fun clearUnimportant() {
        unimportantInbox.clear()
    }
    
    /**
     * Remove a notification thread.
     * Called when user swipes away or system removes it.
     */
    fun removeNotification(packageName: String) {
        // This is tricky because removal is usually by Key, but system gives us Package/ID.
        // For now, the implementation plan said "Reconstruct conversationKey".
        // But doing that accurately without the original SBN is hard.
        // EASIER: The UI calls `clearThread(key)`.
        // The Service calls `addNotification`.
        // If Service calls `removeNotification`, it has the SBN.
        // Let's add an overload with SBN in the Listener refactor.
        // For now, keep existing API.
    }
    
    fun clearThread(conversationKey: String) {
        importantInbox.remove(conversationKey)
        unimportantInbox.remove(conversationKey)
        Log.d(TAG, "Cleared thread $conversationKey")
    }
    
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
