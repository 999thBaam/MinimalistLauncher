package com.minimalist.launcher.data

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Process
import java.util.Calendar

/**
 * Repository for fetching installed apps that can be launched.
 * Filters out apps without launch intents and sorts alphabetically.
 */
class AppRepository(private val context: Context) {

    // 30 days in milliseconds
    private val UNUSED_THRESHOLD_MS = 30L * 24 * 60 * 60 * 1000
    
    private val prefs by lazy {
        context.getSharedPreferences("minimalist_prefs", Context.MODE_PRIVATE)
    }

    /**
     * Fetches all launchable apps, sorted:
     * 1. Pinned apps first
     * 2. Recently used apps (Newest first)
     * 3. Alphabetically by label
     */
    fun getInstalledApps(): List<AppItem> {
        val packageManager = context.packageManager
        
        // Map of PackageName -> LastTimeUsed (Timestamp)
        val usageMap = getUsageMap()
        val pinnedPackages = getPinnedPackages()
        
        // Query for all apps with a launcher intent
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfoList: List<ResolveInfo> = packageManager.queryIntentActivities(
            mainIntent,
            PackageManager.MATCH_ALL
        )

        val hasPermission = hasUsageStatsPermission()
        val currentTime = System.currentTimeMillis()
        val thresholdTime = currentTime - UNUSED_THRESHOLD_MS

        return resolveInfoList
            .asSequence()
            // Exclude this launcher itself
            .filter { it.activityInfo.packageName != context.packageName }
            // Map to our lightweight data class
            .map { resolveInfo ->
                val pkgName = resolveInfo.activityInfo.packageName
                val lastUsed = usageMap[pkgName] ?: 0L
                
                // Unused logic: Permission granted AND last used time is older than 30 days (or never used)
                val isUnused = hasPermission && (lastUsed < thresholdTime)
                val isPinned = pinnedPackages.contains(pkgName)
                
                AppItem(
                    label = resolveInfo.loadLabel(packageManager).toString(),
                    packageName = pkgName,
                    isUnused = isUnused,
                    isPinned = isPinned,
                    lastUsedTime = lastUsed
                )
            }
            // Remove duplicates (some apps register multiple activities)
            .distinctBy { it.packageName }
            // Sort: Pinned (desc), Last Used (desc), Label (asc)
            .sortedWith(
                compareByDescending<AppItem> { it.isPinned }
                    .thenByDescending { it.lastUsedTime }
                    .thenBy { it.label.lowercase() }
            )
            .toList()
    }
    
    fun pinApp(packageName: String) {
        val current = getPinnedPackages().toMutableSet()
        current.add(packageName)
        prefs.edit().putStringSet("pinned_apps", current).apply()
    }
    
    fun unpinApp(packageName: String) {
        val current = getPinnedPackages().toMutableSet()
        current.remove(packageName)
        prefs.edit().putStringSet("pinned_apps", current).apply()
    }
    
    private fun getPinnedPackages(): Set<String> {
        return prefs.getStringSet("pinned_apps", emptySet()) ?: emptySet()
    }

    /**
     * Checks if the user has granted Usage Access permission.
     */
    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Returns a map of package names to their last used timestamp.
     * Queries usage stats for the last 30 days.
     */
    private fun getUsageMap(): Map<String, Long> {
        if (!hasUsageStatsPermission()) return emptyMap()

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - UNUSED_THRESHOLD_MS // Look back 30 days

        // queryAndAggregateUsageStats returns Map<String, UsageStats>
        val usageStats = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
        
        return usageStats.mapValues { it.value.lastTimeUsed }
    }

    /**
     * Gets the launch intent for a specific package.
     * Returns null if the app cannot be launched.
     */
    fun getLaunchIntent(packageName: String): Intent? {
        return context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
