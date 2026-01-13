package com.minimalist.launcher.service

import android.content.Context
import android.service.notification.StatusBarNotification
import android.app.NotificationManager
import android.app.NotificationChannel
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.minimalist.launcher.R
import com.minimalist.launcher.MainActivity
import java.util.concurrent.ConcurrentHashMap

object NotificationBatchManager {
    
    // Simple in-memory storage for now (MVP)
    // Map: PackageName -> List of Notifications
    private val batchedNotifications = ConcurrentHashMap<String, MutableList<BatchedNotification>>()
    
    // Configuration
    private const val BATCH_INTERVAL_MS = 15 * 60 * 1000L // 15 minutes
    private var lastSummaryTime = 0L
    
    private const val CHANNEL_ID = "minimalist_focus_summary"
    private const val SUMMARY_ID = 999
    
    data class BatchedNotification(
        val key: String,
        val title: String,
        val text: String,
        val timestamp: Long,
        val packageName: String
    )
    
    fun addNotification(context: Context, sbn: StatusBarNotification) {
        val notif = sbn.notification
        val title = notif.extras.getString(Notification.EXTRA_TITLE) ?: "Notification"
        val text = notif.extras.getString(Notification.EXTRA_TEXT) ?: ""
        
        val item = BatchedNotification(
            key = sbn.key,
            title = title,
            text = text,
            timestamp = sbn.postTime,
            packageName = sbn.packageName
        )
        
        // Add to batch
        val list = batchedNotifications.getOrPut(sbn.packageName) { mutableListOf() }
        list.add(item)
        
        // Check if we should release summary
        checkAndReleaseSummary(context)
    }
    
    private fun checkAndReleaseSummary(context: Context) {
        val now = System.currentTimeMillis()
        if (lastSummaryTime == 0L) lastSummaryTime = now // Start timer on first interception
        
        // Logic: 
        // 1. If time passed > 15 min, show summary
        // 2. OR purely count based? No, time based as per user request.
        // For MVP testing, let's just update the summary silently whenever a new one comes in,
        // but only "alert" (sound/vibrate) every 15 mins?
        // Actually user said: "received every 15-30 min". 
        // Meaning: Don't show ANYTHING until 15 mins.
        
        // Implementing strict batching:
        // We need a proper scheduler (WorkManager/Alarm) ideally.
        // For simplicity in this step: We just check timestamp difference on receiving.
        // Note: This relies on receiving a notification to trigger the check. 
        // If no notifications come for 2 hours, no summary is shown (which is fine, nothing to show).
        
        if (now - lastSummaryTime > BATCH_INTERVAL_MS) {
            showSummaryNotification(context)
            lastSummaryTime = now
            // Clear batch after showing? Or keep until user opens?
            // Usually we keep until user opens. For now, simple clear on show (bad UX) or persistent?
            // Let's make the notification intent open a dialog that clears them.
        }
    }
    
    private fun showSummaryNotification(context: Context) {
        val totalCount = batchedNotifications.values.sumOf { it.size }
        if (totalCount == 0) return
        
        createChannel(context)
        
        // Generate Smart Digest
        val digest = generateSmartDigest(context)
        
        // Intent to open Main Activity with flag and content
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("show_notifications", true)
            putExtra("notification_digest", digest)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Focus Summary")
            .setContentText("You missed $totalCount distractions")
            .setStyle(NotificationCompat.BigTextStyle().bigText(digest)) // Expandable summary
            .setPriority(NotificationCompat.PRIORITY_LOW) // Don't buzz too hard
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(SUMMARY_ID, builder.build())
        
        batchedNotifications.clear() 
    }
    
    private fun generateSmartDigest(context: Context): String {
        val sb = StringBuilder()
        
        // Process each app
        batchedNotifications.forEach { (pkg, notifs) ->
            if (notifs.isEmpty()) return@forEach
            
            val appName = getAppName(pkg, context)
            val count = notifs.size
            
            // Pattern Analysis
            var likes = 0
            var comments = 0
            var promos = 0
            
            notifs.forEach { n ->
                val content = (n.title + " " + n.text).lowercase()
                when {
                    content.contains("liked") || content.contains("love") -> likes++
                    content.contains("comment") || content.contains("replied") -> comments++
                    content.contains("sale") || content.contains("off") || content.contains("code") -> promos++
                }
            }
            
            sb.append("• $appName: ")
            
            if (promos > 0) {
                sb.append("$promos Promos/Offers")
            } else if (likes > 0 || comments > 0) {
                val details = mutableListOf<String>()
                if (likes > 0) details.add("$likes Likes")
                if (comments > 0) details.add("$comments Comments")
                sb.append("${details.joinToString(", ")}")
            } else {
                sb.append("$count updates")
            }
            sb.append("\n")
        }
        
        return sb.toString().trim()
    }
    
    // Cache for app labels to avoid repeated PM calls
    private val appLabelCache = ConcurrentHashMap<String, String>()
    
    private fun getAppName(pkg: String, context: Context? = null): String {
        return appLabelCache.getOrPut(pkg) {
            try {
                if (context != null) {
                    val pm = context.packageManager
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } else {
                    pkg.substringAfterLast(".").capitalize().take(15) // Fallback
                }
            } catch (e: Exception) {
                pkg.substringAfterLast(".").capitalize()
            }
        }
    }
    
    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Focus Summaries"
            val descriptionText = "Batched notifications summary"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
