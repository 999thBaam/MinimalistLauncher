package com.minimalist.launcher.data

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Repository for logging and syncing usage analytics to Firebase Firestore
 */
class AnalyticsRepository(private val context: Context) {
    
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val prefs = context.getSharedPreferences("analytics_cache", Context.MODE_PRIVATE)
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    
    /**
     * Log a usage event - stores locally and syncs to Firestore
     */
    fun logEvent(event: UsageEvent) {
        val eventWithId = event.copy(id = UUID.randomUUID().toString())
        
        // Log to Firebase Analytics custom event
        logToFirebaseAnalytics(eventWithId)
        
        // Sync to Firestore if user is authenticated
        syncEventToFirestore(eventWithId)
        
        // Update local daily summary
        updateLocalDailySummary(eventWithId)
        
        Log.d("Analytics", "Event logged: ${event.eventType} - ${event.packageName}")
    }
    
    /**
     * Log app opened event
     */
    fun logAppOpened(packageName: String, isPinned: Boolean, opacityLevel: Float) {
        val eventType = if (isPinned) EventType.PINNED_APP_USED else EventType.APP_OPENED
        
        // Check if this is a ghosted app resurrection
        val finalEventType = if (opacityLevel < 0.5f && !isPinned) {
            EventType.GHOSTED_APP_RESURRECTED
        } else {
            eventType
        }
        
        logEvent(UsageEvent(
            eventType = finalEventType,
            packageName = packageName,
            metadata = mapOf(
                "isPinned" to isPinned,
                "opacityLevel" to opacityLevel
            )
        ))
    }
    
    /**
     * Log distraction resisted - user saw faded app but didn't tap
     */
    fun logDistractionResisted(packageName: String, daysSinceUse: Int) {
        logEvent(UsageEvent(
            eventType = EventType.DISTRACTION_RESISTED,
            packageName = packageName,
            metadata = mapOf(
                "daysSinceUse" to daysSinceUse
            )
        ))
    }
    
    /**
     * Log launcher opened
     */
    fun logLauncherOpened() {
        logEvent(UsageEvent(
            eventType = EventType.LAUNCHER_OPENED
        ))
    }
    
    /**
     * Log search performed
     */
    fun logSearchPerformed(query: String) {
        logEvent(UsageEvent(
            eventType = EventType.SEARCH_PERFORMED,
            metadata = mapOf("queryLength" to query.length)
        ))
    }
    
    /**
     * Log onboarding events
     */
    fun logOnboardingStarted() {
        logEvent(UsageEvent(eventType = EventType.ONBOARDING_STARTED))
    }
    
    fun logOnboardingCompleted() {
        logEvent(UsageEvent(eventType = EventType.ONBOARDING_COMPLETED))
    }
    
    fun logPermissionGranted(permissionType: String) {
        logEvent(UsageEvent(
            eventType = EventType.PERMISSION_GRANTED,
            metadata = mapOf("permissionType" to permissionType)
        ))
    }
    
    /**
     * Log to Firebase Analytics
     */
    private fun logToFirebaseAnalytics(event: UsageEvent) {
        try {
            val bundle = android.os.Bundle().apply {
                putString("event_type", event.eventType.name)
                event.packageName?.let { putString("package_name", it) }
                event.durationMs?.let { putLong("duration_ms", it) }
                event.metadata?.forEach { (key, value) ->
                    when (value) {
                        is String -> putString(key, value)
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is Float -> putFloat(key, value)
                        is Boolean -> putBoolean(key, value)
                    }
                }
            }
            
            com.google.firebase.analytics.FirebaseAnalytics.getInstance(context)
                .logEvent(event.eventType.name.lowercase(), bundle)
        } catch (e: Exception) {
            Log.e("Analytics", "Failed to log to Firebase Analytics", e)
        }
    }
    
    /**
     * Sync event to Firestore
     */
    private fun syncEventToFirestore(event: UsageEvent) {
        val userId = auth.currentUser?.uid ?: return
        
        val eventDoc = firestore
            .collection("users")
            .document(userId)
            .collection("events")
            .document(event.id)
        
        eventDoc.set(event.toMap())
            .addOnSuccessListener {
                Log.d("Analytics", "Event synced to Firestore: ${event.id}")
            }
            .addOnFailureListener { e ->
                Log.e("Analytics", "Failed to sync event", e)
                // Cache for later retry
                cacheEventForRetry(event)
            }
    }
    
    /**
     * Update local daily summary
     */
    private fun updateLocalDailySummary(event: UsageEvent) {
        val today = dateFormat.format(Date())
        val key = "summary_$today"
        
        val currentOpens = prefs.getInt("${key}_opens", 0)
        val currentResists = prefs.getInt("${key}_resists", 0)
        val currentPinned = prefs.getInt("${key}_pinned", 0)
        val currentGhosted = prefs.getInt("${key}_ghosted", 0)
        val currentLauncher = prefs.getInt("${key}_launcher", 0)
        
        prefs.edit().apply {
            when (event.eventType) {
                EventType.APP_OPENED -> putInt("${key}_opens", currentOpens + 1)
                EventType.PINNED_APP_USED -> putInt("${key}_pinned", currentPinned + 1)
                EventType.DISTRACTION_RESISTED -> putInt("${key}_resists", currentResists + 1)
                EventType.GHOSTED_APP_RESURRECTED -> putInt("${key}_ghosted", currentGhosted + 1)
                EventType.LAUNCHER_OPENED -> putInt("${key}_launcher", currentLauncher + 1)
                else -> { /* Other events */ }
            }
            apply()
        }
        
        // Also sync daily summary to Firestore
        syncDailySummaryToFirestore(today)
    }
    
    /**
     * Sync daily summary to Firestore
     */
    private fun syncDailySummaryToFirestore(date: String) {
        val userId = auth.currentUser?.uid ?: return
        val key = "summary_$date"
        
        val summary = DailySummary(
            date = date,
            totalAppOpens = prefs.getInt("${key}_opens", 0),
            distractionsResisted = prefs.getInt("${key}_resists", 0),
            pinnedAppUsage = prefs.getInt("${key}_pinned", 0),
            ghostedAppsOpened = prefs.getInt("${key}_ghosted", 0),
            launcherVisits = prefs.getInt("${key}_launcher", 0)
        )
        
        firestore
            .collection("users")
            .document(userId)
            .collection("dailySummaries")
            .document(date)
            .set(summary.toMap(), SetOptions.merge())
    }
    
    /**
     * Cache event for retry (when offline)
     */
    private fun cacheEventForRetry(event: UsageEvent) {
        val cachedEvents = prefs.getStringSet("cached_events", mutableSetOf()) ?: mutableSetOf()
        cachedEvents.add("${event.id}|${event.eventType.name}|${event.packageName ?: ""}|${event.timestamp}")
        prefs.edit().putStringSet("cached_events", cachedEvents).apply()
    }
    
    /**
     * Retry syncing cached events
     */
    fun retryCachedEvents() {
        if (auth.currentUser == null) return
        
        val cachedEvents = prefs.getStringSet("cached_events", mutableSetOf()) ?: return
        if (cachedEvents.isEmpty()) return
        
        cachedEvents.forEach { eventStr ->
            val parts = eventStr.split("|")
            if (parts.size >= 4) {
                val event = UsageEvent(
                    id = parts[0],
                    eventType = EventType.valueOf(parts[1]),
                    packageName = parts[2].takeIf { it.isNotEmpty() },
                    timestamp = parts[3].toLongOrNull() ?: System.currentTimeMillis()
                )
                syncEventToFirestore(event)
            }
        }
        
        // Clear cache after retry
        prefs.edit().remove("cached_events").apply()
    }
    
    /**
     * Get today's summary
     */
    fun getTodaySummary(): DailySummary {
        val today = dateFormat.format(Date())
        val key = "summary_$today"
        
        return DailySummary(
            date = today,
            totalAppOpens = prefs.getInt("${key}_opens", 0),
            distractionsResisted = prefs.getInt("${key}_resists", 0),
            pinnedAppUsage = prefs.getInt("${key}_pinned", 0),
            ghostedAppsOpened = prefs.getInt("${key}_ghosted", 0),
            launcherVisits = prefs.getInt("${key}_launcher", 0)
        )
    }
}
