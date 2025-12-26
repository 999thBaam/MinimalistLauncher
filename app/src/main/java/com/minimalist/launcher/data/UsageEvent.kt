package com.minimalist.launcher.data

/**
 * Usage event for tracking app interactions and distraction patterns
 */
data class UsageEvent(
    val id: String = "",
    val eventType: EventType,
    val packageName: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val durationMs: Long? = null,
    val metadata: Map<String, Any>? = null
) {
    /**
     * Convert to Firestore document map
     */
    fun toMap(): Map<String, Any?> = mapOf(
        "eventType" to eventType.name,
        "packageName" to packageName,
        "timestamp" to timestamp,
        "durationMs" to durationMs,
        "metadata" to metadata
    )
    
    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): UsageEvent {
            return UsageEvent(
                id = id,
                eventType = EventType.valueOf(map["eventType"] as? String ?: "APP_OPENED"),
                packageName = map["packageName"] as? String,
                timestamp = (map["timestamp"] as? Long) ?: System.currentTimeMillis(),
                durationMs = map["durationMs"] as? Long,
                metadata = @Suppress("UNCHECKED_CAST") (map["metadata"] as? Map<String, Any>)
            )
        }
    }
}

/**
 * Types of usage events to track
 */
enum class EventType {
    // App interactions
    APP_OPENED,              // User opened an app
    APP_CLOSED,              // User left an app (estimated)
    
    // Launcher interactions
    LAUNCHER_OPENED,         // User returned to launcher
    SEARCH_PERFORMED,        // User searched for an app
    
    // Distraction metrics
    DISTRACTION_RESISTED,    // User saw faded/ghosted app but didn't tap
    PINNED_APP_USED,         // User opened a pinned app (intentional use)
    GHOSTED_APP_RESURRECTED, // User opened an app that had faded (potential relapse)
    
    // Session events
    SESSION_START,           // App usage session started
    SESSION_END,             // App usage session ended
    
    // Onboarding
    ONBOARDING_STARTED,
    ONBOARDING_COMPLETED,
    PERMISSION_GRANTED
}

/**
 * Daily usage summary for aggregated stats
 */
data class DailySummary(
    val date: String,  // YYYY-MM-DD format
    val totalAppOpens: Int = 0,
    val totalScreenTimeMs: Long = 0,
    val distractionsResisted: Int = 0,
    val pinnedAppUsage: Int = 0,
    val ghostedAppsOpened: Int = 0,
    val launcherVisits: Int = 0,
    val appBreakdown: Map<String, Int> = emptyMap()  // packageName -> openCount
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "date" to date,
        "totalAppOpens" to totalAppOpens,
        "totalScreenTimeMs" to totalScreenTimeMs,
        "distractionsResisted" to distractionsResisted,
        "pinnedAppUsage" to pinnedAppUsage,
        "ghostedAppsOpened" to ghostedAppsOpened,
        "launcherVisits" to launcherVisits,
        "appBreakdown" to appBreakdown
    )
}
