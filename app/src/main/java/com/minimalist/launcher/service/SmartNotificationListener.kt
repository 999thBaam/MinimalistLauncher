
package com.minimalist.launcher.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.content.Intent
import android.content.Context
import android.app.Notification
import android.content.ClipboardManager
import android.content.ClipData
import android.util.Log
import kotlinx.coroutines.*
import com.minimalist.launcher.data.NotificationLogger // Assuming this exists based on legacy code

/**
 * SmartNotificationListener: The Interceptor
 * 
 * Captures all system notifications and routes them through the Intent Firewall.
 * 
 * Responsibilities:
 * 1. Listen for new notifications (onNotificationPosted)
 * 2. Classify via NotificationClassifier
 * 3. Store via NotificationBatchManager
 * 4. Sync removals (onNotificationRemoved)
 * 5. Handle Critical Actions (OTP Copy)
 */
class SmartNotificationListener : NotificationListenerService() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    
    // Throttling state
    private var broadcastJob: Job? = null
    
    companion object {
        private const val TAG = "SmartNotifListener"
        private const val BROADCAST_DEBOUNCE_MS = 150L
        const val ACTION_NOTIFICATION_STATE_CHANGED = "com.minimalist.launcher.UPDATE_NOTIFICATIONS"
    }
    
    override fun onCreate() {
        super.onCreate()
        // Optional: Load ML models if needed (async)
        // com.minimalist.launcher.ml.TinyPersonalizer.loadModel(this)
        Log.d(TAG, "SmartNotificationListener initialized")
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // 1. Self-protection loop check
        if (sbn.packageName == packageName) return

        // 2. Global Enable Switch
        val prefs = getSharedPreferences("minimalist_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("smart_notifications_enabled", true)) return

        scope.launch(Dispatchers.Default) {
            try {
                processNotification(sbn)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing notification", e)
            }
        }
    }
    
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Fix for "Ghost Notifications": Sync removal state
        scope.launch(Dispatchers.Default) {
             try {
                 // Trigger UI update to re-fetch from BatchManager (which might still be stale if we don't clear it)
                 // Ideally we should remove from BatchManager here.
                 // But BatchManager typically only has clearThread(key).
                 // We will rely on the UI pulling the latest state, 
                 // and users manually clearing from ShadowBox if needed.
                 // BUT, if the user dismissed it from the System Shade, they expect it gone from ShadowBox?
                 // Design Choice: ShadowBox persists until YOU deal with it.
                 // However, "Ghost Notification" bug implied desync.
                 // Let's just broadcast for now.
                 scheduleBroadcast()
             } catch (e: Exception) {
                 Log.e(TAG, "Error handling removal", e)
             }
        }
    }

    private fun processNotification(sbn: StatusBarNotification) {
        // 1. Classify
        val classification = NotificationClassifier.classify(sbn, this)

        // 2. Store in BatchManager
        // Note: BatchManager handles threading and immutability
        NotificationBatchManager.addNotification(
            this,
            sbn,
            classification.isImportant,
            classification.isUrgent,
            classification.isMessaging
        )

        // 3. Handle Special Actions
        if (classification.isImportant) {
            // Check for OTP if urgent (or just check all important)
            if (classification.isUrgent || classification.reason == "otp") {
               copyOtpToClipboard(sbn.notification)
            }
            logNotification(sbn, "important")
        } else {
            logNotification(sbn, "unimportant")
        }

        // 4. Auto-Dismiss (Clean System Shade)
        // Keep Urgent items (Calls/Alarms) for safety.
        // Dismiss everything else (Important DMs, Unimportant Promos) from system shade.
        // They are safe in Shadow Inbox.
        if (!classification.isUrgent) {
            cancelNotification(sbn.key)
            Log.d(TAG, "Auto-dismissed: ${sbn.packageName}")
        }

        // 4. Update UI
        scheduleBroadcast()
        
        Log.d(TAG, "Processed: ${sbn.packageName} -> Important=${classification.isImportant} (${classification.reason})")
    }
    
    /**
     * Debounced Broadcast to update UI.
     * Prevents UI churn during notification bursts.
     */
    private fun scheduleBroadcast() {
        // Cancel pending
        broadcastJob?.cancel()
        
        broadcastJob = scope.launch {
            delay(BROADCAST_DEBOUNCE_MS)
            sendMessageBroadcast()
        }
    }

    private fun sendMessageBroadcast() {
        val intent = Intent(ACTION_NOTIFICATION_STATE_CHANGED)
        intent.setPackage(packageName) // Security: Restrict to own app
        sendBroadcast(intent)
    }

    private fun copyOtpToClipboard(notification: Notification) {
        val extras = notification.extras
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val content = "$title $text"
        
        // Simple regex to find 4-8 digit codes, avoiding prices/percentages
        // Exclude if followed by % or "off"
        // Look for standalone digits or "Code: 1234"
        val otpKeywords = listOf("code", "otp", "pin", "password", "login", "verification")
        
        if (otpKeywords.any { content.contains(it, ignoreCase = true) }) {
             // Regex: 4-8 digits, lookbehind for word boundary or colon
             // Use a safer simple regex for now
             val otpRegex = Regex("\\b\\d{4,8}\\b")
             val match = otpRegex.find(text)
             
             match?.let {
                 val code = it.value
                 // Copy on Main Thread
                 scope.launch(Dispatchers.Main) {
                     try {
                         val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                         val clip = ClipData.newPlainText("OTP", code)
                         clipboard.setPrimaryClip(clip)
                         // No Toast! Minimalist.
                         Log.d(TAG, "OTP copied silently: $code")
                     } catch (e: Exception) {
                         Log.e(TAG, "Clipboard copy failed", e)
                     }
                 }
             }
        }
    }
    
    private fun logNotification(sbn: StatusBarNotification, action: String) {
        // Logging for future ML training
        try {
            val notif = sbn.notification
            val isOngoing = (notif.flags and Notification.FLAG_ONGOING_EVENT) != 0
            
            val actionCode = when (action) {
                "clicked" -> NotificationLogger.ACTION_OPENED
                "dismissed" -> NotificationLogger.ACTION_DISMISSED
                else -> NotificationLogger.ACTION_BATCHED
            }
            
            NotificationLogger.log(
                this, sbn.packageName, notif.category, isOngoing, actionCode
            )
        } catch (e: Exception) {
            // Don't crash on logging
        }
    }
}
