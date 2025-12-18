package com.minimalist.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo

/**
 * Repository for fetching installed apps that can be launched.
 * Filters out apps without launch intents and sorts alphabetically.
 */
class AppRepository(private val context: Context) {

    /**
     * Fetches all launchable apps, sorted alphabetically by label.
     * Excludes:
     * - System apps without launch intents
     * - This launcher itself (to prevent recursion)
     */
    fun getInstalledApps(): List<AppItem> {
        val packageManager = context.packageManager
        
        // Query for all apps with a launcher intent
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfoList: List<ResolveInfo> = packageManager.queryIntentActivities(
            mainIntent,
            PackageManager.MATCH_ALL
        )

        return resolveInfoList
            .asSequence()
            // Exclude this launcher itself
            .filter { it.activityInfo.packageName != context.packageName }
            // Map to our lightweight data class
            .map { resolveInfo ->
                AppItem(
                    label = resolveInfo.loadLabel(packageManager).toString(),
                    packageName = resolveInfo.activityInfo.packageName
                )
            }
            // Remove duplicates (some apps register multiple activities)
            .distinctBy { it.packageName }
            // Sort alphabetically (case-insensitive)
            .sortedBy { it.label.lowercase() }
            .toList()
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
