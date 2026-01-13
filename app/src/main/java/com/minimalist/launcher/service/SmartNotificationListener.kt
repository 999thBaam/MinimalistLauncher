package com.minimalist.launcher.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class SmartNotificationListener : NotificationListenerService() {
    
    override fun onCreate() {
        super.onCreate()
        // Load the personal model if it exists
        com.minimalist.launcher.ml.TinyPersonalizer.loadModel(this)
    }
    
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Ignore our own notifications to avoid loops
        if (sbn.packageName == packageName) return
        
        // Ignore persistent/ongoing notifications (Music, Nav, etc should pass)
        // Handled by Classifier, but good double check if we want to skip processing entirely
        
        // CHECK USER PREFERENCE
        val prefs = getSharedPreferences("minimalist_prefs", android.content.Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("smart_notifications_enabled", true)
        
        // If "Choice" is OFF, let everything pass through normal system
        if (!isEnabled) return
        
        // Check if important (Calls, Messages, etc)
        var isImportant = NotificationClassifier.isImportant(sbn)
        
        // ML OVERRIDE (Personalization)
        // If Classifier says "Unimportant", ask the Model "Are you sure?"
        if (!isImportant) {
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val prediction = com.minimalist.launcher.ml.TinyPersonalizer.predict(
                sbn.packageName, 
                sbn.notification.category, 
                hour
            )
            
            // If Model is very confident (e.g. > 80% chance user opens it), preserve it.
            if (prediction > 0.8f) {
                android.util.Log.d("TinyML", "Rescued ${sbn.packageName} from batch! Score: $prediction")
                isImportant = true
            }
        }
        
        if (!isImportant) {
             // Unimportant -> Batch it
             NotificationBatchManager.addNotification(this, sbn)
             
             // Log as Batched
             logNotification(sbn, true)
             
             // SAFETY: Delay cancellation to avoid race conditions on some ROMs
             android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try { cancelNotification(sbn.key) } catch (e: Exception) {}
             }, 200) // 200ms breathing room
             
        } else {
            // Important -> Check for OTP codes to auto-copy
            checkForOtp(sbn.notification)
            
            // REMOVED: logNotification(sbn, false) 
            // Reason: Label Leakage. We don't know if user liked it yet.
            // We only log if they CLICK it (in onNotificationRemoved).
        }
    }
    
    private fun checkForOtp(notification: android.app.Notification) {
        val extras = notification.extras
        val title = extras.getString(android.app.Notification.EXTRA_TITLE) ?: ""
        val text = extras.getString(android.app.Notification.EXTRA_TEXT) ?: ""
        val content = "$title $text"
        
        // Regex for 4-8 digit codes
        // Negative lookbehind/ahead for currency ($, ₹, Rs, INR) and "off"
        val otpKeywords = listOf("code", "otp", "pin", "password", "login", "verification")
        val hasKeyword = otpKeywords.any { content.contains(it, ignoreCase = true) }
        
        if (hasKeyword) {
            // Regex explanation:
            // (?<!...) Negative lookbehind for currency symbols (₹, $) or "Rs"
            // \b Word boundary
            // \d{4,8}  4 to 8 digits
            // \b Word boundary
            // (?!...) Negative lookahead for "%", " off"
            val otpRegex = Regex("(?<![₹$]|Rs\\.?\\s?)(\\b\\d{4,8}\\b)(?!\\s?%|\\s?off)")
            val match = otpRegex.find(content)
            
            match?.value?.let { otp ->
                copyToClipboard(otp)
            }
        }
    }
    
    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap?, reason: Int) {
        // True Intent Logging
        // We only care about user actions, not system cancels.
        
        when (reason) {
            REASON_CLICK -> {
                // User CLICKED it -> Positive Signal (1.0)
                // This is the only true "Important" signal.
                logNotification(sbn, isBatched = false, isClick = true)
            }
            REASON_CANCEL, REASON_CANCEL_ALL -> {
                // User DISMISSED it -> Negative Signal (0.0)
                // Log as explicit negative.
                logNotification(sbn, isBatched = false, isClick = false, isDismiss = true)
            }
            // Ignore REASON_APP_CANCEL, REASON_LISTENER_CANCEL (our batching), REASON_ERROR, etc.
        }
    }
    
    // Updated signature for versatility
    private fun logNotification(sbn: StatusBarNotification, isBatched: Boolean, isClick: Boolean = false, isDismiss: Boolean = false) {
        val notif = sbn.notification
        val isOngoing = (notif.flags and android.app.Notification.FLAG_ONGOING_EVENT) != 0
        
        var action = com.minimalist.launcher.data.NotificationLogger.ACTION_BATCHED
        if (isClick) action = com.minimalist.launcher.data.NotificationLogger.ACTION_OPENED
        if (isDismiss) action = com.minimalist.launcher.data.NotificationLogger.ACTION_DISMISSED
        if (isBatched) action = com.minimalist.launcher.data.NotificationLogger.ACTION_BATCHED
        
        // Log it
        com.minimalist.launcher.data.NotificationLogger.log(
            this,
            sbn.packageName,
            notif.category,
            isOngoing,
            action
        )
    }
    private fun copyToClipboard(otp: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("OTP", otp)
                clipboard.setPrimaryClip(clip)
                android.widget.Toast.makeText(this, "OTP Copied: $otp", android.widget.Toast.LENGTH_SHORT).show()
                Log.d("SmartNotif", "OTP Copied: $otp")
            } catch (e: Exception) {
                Log.e("SmartNotif", "Clipboard error", e)
            }
        }
    }
}
