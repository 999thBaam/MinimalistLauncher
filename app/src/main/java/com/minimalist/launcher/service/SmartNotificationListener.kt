package com.minimalist.launcher.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * SmartNotificationListener: 2-Box Attention Routing
 * 
 * Routes all notifications into 2 boxes via NotificationBatchManager.
 * 
 * BOX 1: IMPORTANT (VIP, DMs, calls)
 * BOX 2: UNIMPORTANT (Everything else)
 * 
 * Broadcasts updates to UI.
 */
class SmartNotificationListener : NotificationListenerService() {
    
    companion object {
        private const val TAG = "AttentionRouter"
        
        // Broadcast action for UI updates
        const val ACTION_NOTIFICATION_STATE_CHANGED = "com.minimalist.launcher.NOTIFICATION_STATE"
        const val EXTRA_IMPORTANT_COUNT = "important_count"
        const val EXTRA_UNIMPORTANT_COUNT = "unimportant_count"
        const val EXTRA_IS_URGENT = "is_urgent"
    }
    
    override fun onCreate() {
        super.onCreate()
        com.minimalist.launcher.ml.TinyPersonalizer.loadModel(this)
        Log.d(TAG, "2-Box Attention Router initialized")
    }
    
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        
        val prefs = getSharedPreferences("minimalist_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("smart_notifications_enabled", true)) return
        
        // Classify
        val classification = NotificationClassifier.classify(sbn, this)
        
        Log.d(TAG, "${sbn.packageName} → important=${classification.isImportant}, urgent=${classification.isUrgent}")
        
        // Store in central manager
        NotificationBatchManager.addNotification(
            this, 
            sbn, 
            classification.isImportant, 
            classification.isUrgent
        )
        
        if (classification.isImportant) {
            handleImportant(sbn, classification.isUrgent)
        } else {
            handleUnimportant(sbn)
        }
        
        broadcastState()
    }
    
    private fun handleImportant(sbn: StatusBarNotification, isUrgent: Boolean) {
        if (isUrgent) {
            // Check for OTP to auto-copy
            checkForOtp(sbn.notification)
        }
        
        // Log for ML
        logNotification(sbn, "important")
    }
    
    private fun handleUnimportant(sbn: StatusBarNotification) {
        // Log for ML
        logNotification(sbn, "unimportant")
        
        // Cancel the actual notification so it doesn't clutter status bar
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                cancelNotification(sbn.key)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cancel", e)
            }
        }, 200)
    }
    
    private fun broadcastState() {
        val intent = Intent(ACTION_NOTIFICATION_STATE_CHANGED).apply {
            putExtra(EXTRA_IMPORTANT_COUNT, NotificationBatchManager.getImportantCount())
            putExtra(EXTRA_UNIMPORTANT_COUNT, NotificationBatchManager.getUnimportantCount())
            putExtra(EXTRA_IS_URGENT, NotificationBatchManager.hasUrgent())
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }
    
    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap?, reason: Int) {
        // We generally don't remove from our store on dismissal from system, 
        // because we might have cancelled it ourselves (unimportant).
        // Removal logic is handled by UI (when user handles them).
        
        // However, if user dismisses an IMPORTANT notification from status bar, 
        // maybe we should clear it from our Important box?
        // For now, let's keep them in sync only if explicitly handled.
        
        // Learn from user actions
        when (reason) {
            REASON_CLICK -> logNotification(sbn, "clicked")
            REASON_CANCEL, REASON_CANCEL_ALL -> logNotification(sbn, "dismissed")
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // Utilities
    // ════════════════════════════════════════════════════════════════════════
    
    private fun checkForOtp(notification: android.app.Notification) {
        val extras = notification.extras
        val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
        val content = "$title $text"
        
        val otpKeywords = listOf("code", "otp", "pin", "password", "login", "verification")
        if (otpKeywords.any { content.contains(it, ignoreCase = true) }) {
            val otpRegex = Regex("(?<![₹$]|Rs\\.?\\s?)(\\b\\d{4,8}\\b)(?!\\s?%|\\s?off)")
            otpRegex.find(content)?.value?.let { otp ->
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    try {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("OTP", otp))
                        android.widget.Toast.makeText(this, "OTP Copied: $otp", android.widget.Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {}
                }
            }
        }
    }
    
    private fun logNotification(sbn: StatusBarNotification, action: String) {
        val notif = sbn.notification
        val isOngoing = (notif.flags and android.app.Notification.FLAG_ONGOING_EVENT) != 0
        
        val actionCode = when (action) {
            "clicked" -> com.minimalist.launcher.data.NotificationLogger.ACTION_OPENED
            "dismissed" -> com.minimalist.launcher.data.NotificationLogger.ACTION_DISMISSED
            else -> com.minimalist.launcher.data.NotificationLogger.ACTION_BATCHED
        }
        
        com.minimalist.launcher.data.NotificationLogger.log(
            this, sbn.packageName, notif.category, isOngoing, actionCode
        )
    }
}
