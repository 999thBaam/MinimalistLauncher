package com.minimalist.launcher.service

import android.service.notification.StatusBarNotification
import android.app.Notification

object NotificationClassifier {
    
    // Important Categories (Pass-through)
    private val IMPORTANT_CATEGORIES = setOf(
        Notification.CATEGORY_CALL,
        Notification.CATEGORY_MESSAGE,
        Notification.CATEGORY_ALARM,
        Notification.CATEGORY_EVENT,
        Notification.CATEGORY_REMINDER,
        Notification.CATEGORY_ERROR,
        Notification.CATEGORY_NAVIGATION
    )
    
    // Unimportant Categories (Batch)
    private val UNIMPORTANT_CATEGORIES = setOf(
        Notification.CATEGORY_SOCIAL,
        Notification.CATEGORY_PROMO,
        Notification.CATEGORY_RECOMMENDATION
        // Removed CATEGORY_SERVICE to defer check
    )

    // Important Packages (Whitelist)
    private val IMPORTANT_PACKAGES = setOf(
        "com.google.android.dialer", // Phone
        "com.android.phone",
        "com.whatsapp",
        "org.telegram.messenger",
        "com.google.android.calendar",
        "com.google.android.apps.messaging", // SMS
        "com.android.messaging"
    )
    
    // Unimportant Packages (Blacklist for batching)
    private val BATCH_PACKAGES = setOf(
        "com.instagram.android",
        "com.facebook.katana",
        "com.twitter.android",
        "com.zhiliaoapp.musically", // TikTok
        "com.snapchat.android",
        "com.pinterest",
        "com.linkedin.android"
    )
    
    fun isImportant(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification
        val pkg = sbn.packageName
        
        // 1. Check Explicit Whitelist (Messaging/Phone)
        if (IMPORTANT_PACKAGES.contains(pkg)) return true
        
        // 2. Check Notification Category (Excluding Service)
        if (notification.category != null) {
            if (IMPORTANT_CATEGORIES.contains(notification.category)) return true
            if (UNIMPORTANT_CATEGORIES.contains(notification.category)) return false
        }
        
        // 3. Check for Ongoing/Foreground Service (Important!)
        val isOngoing = (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
        val isForeground = (notification.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0
        if (isOngoing || isForeground) return true
        
        // 4. Now handle generic Service category if it wasn't flagged as ongoing/foreground
        if (Notification.CATEGORY_SERVICE == notification.category) {
            return false // Service without foreground flag -> Unimportant
        }
        
        // 5. Blacklist check
        if (BATCH_PACKAGES.contains(pkg)) return false
        
        // Default safe
        return true
    }
}
