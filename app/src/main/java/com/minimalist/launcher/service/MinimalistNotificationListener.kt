package com.minimalist.launcher.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.minimalist.launcher.model.NotificationUiModel
import java.util.concurrent.ConcurrentHashMap

/**
 * Listens for system notifications and exposes them to the Minimalist Notification Center.
 */
class MinimalistNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "MinNotifListener"
        private var instance: MinimalistNotificationListener? = null
        
        // Vault: Local storage for auto-dismissed notifications
        private val vault = ConcurrentHashMap<String, StatusBarNotification>()
        
        // Expanded State: Packages that are currently expanded
        private val expandedPackages = mutableSetOf<String>()
        
        // Expose active models via LiveData
        private val _activeModels = MutableLiveData<List<NotificationUiModel>>()
        val activeModels: LiveData<List<NotificationUiModel>> = _activeModels
        
        fun togglePackageExpansion(packageName: String) {
            if (expandedPackages.contains(packageName)) {
                expandedPackages.remove(packageName)
            } else {
                expandedPackages.add(packageName)
            }
            instance?.updateNotificationList()
        }
        
        fun dismissNotification(keys: List<String>) {
            keys.forEach { key ->
                vault.remove(key)
                instance?.cancelNotification(key)
            }
            instance?.updateNotificationList()
        }
        
        fun dismissNotification(key: String) {
            dismissNotification(listOf(key))
        }
        
        fun clearAllNotifications() {
            vault.clear()
            expandedPackages.clear()
            instance?.cancelAllNotifications()
            instance?.updateNotificationList()
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.d(TAG, "Listener Connected")
        updateNotificationList()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        vault.clear()
        expandedPackages.clear()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Listener Disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!shouldInclude(sbn)) return

        val n = sbn.notification
        val isOngoing = sbn.isOngoing
        val isMedia = n.extras.containsKey(android.app.Notification.EXTRA_MEDIA_SESSION) ||
                      (n.extras.getString(android.app.Notification.EXTRA_TEMPLATE)?.contains("MediaStyle") == true)
        val isCall = n.category == android.app.Notification.CATEGORY_CALL
        
        if (isOngoing || isMedia || isCall) {
            updateNotificationList()
        } else {
            // Add to Vault Immediately (Instance UI Update)
            vault[sbn.key] = sbn
            updateNotificationList()
            
            // GRACE PERIOD: Delay system dismissal by 2 seconds
            // This ensures the notification sound/vibration has time to play fully.
            // Converting "Inbox Mode" -> "Ghost Mode" (Rings then Vanishes)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    cancelNotification(sbn.key)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to auto-dismiss", e)
                }
            }, 2000) // 2 Seconds
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        updateNotificationList()
    }

    private fun updateNotificationList() {
        try {
            // 1. Get All Items
            val systemActive = try {
                super.getActiveNotifications()?.toList() ?: emptyList()
            } catch (e: Exception) { emptyList() }
            
            val vaultItems = vault.values.toList()
            val combined = (systemActive + vaultItems)
                .filter { shouldInclude(it) }
                .distinctBy { it.key }
            
            // 2. Group By Package
            val groupedMap = combined.groupBy { it.packageName }
            
            // Auto-Collapse: If a package has no notifications left, remove it from expanded state
            // This ensures that when new notifications arrive later, they start Collapsed.
            expandedPackages.retainAll(groupedMap.keys)
            
            // 3. Sort Packages by "Newest Notification Time"
            // This ensures the Package Blocks are ordered by time, but items INSIDE stay together.
            val sortedPackages = groupedMap.entries.sortedByDescending { entry ->
                entry.value.maxOfOrNull { it.postTime } ?: 0L
            }
            
            val finalModels = ArrayList<NotificationUiModel>()
            
            for ((pkg, items) in sortedPackages) {
                // Sort items inside the package (Newest first)
                val sortedItems = items.sortedByDescending { it.postTime }
                val representative = sortedItems.first()
                val count = sortedItems.size
                
                val n = representative.notification
                val isMedia = n.extras.containsKey(android.app.Notification.EXTRA_MEDIA_SESSION) ||
                              (n.extras.getString(android.app.Notification.EXTRA_TEMPLATE)?.contains("MediaStyle") == true)
                
                // EXPANSION LOGIC
                // Check if expanded OR if it's Media (Media never stacks) OR if count is 1
                if (expandedPackages.contains(pkg) || isMedia || count == 1) {
                    // Render Individual Items
                    sortedItems.forEach { item ->
                         finalModels.add(NotificationUiModel(
                            key = item.key,
                            sbn = item,
                            isGroup = false,
                            count = 1,
                            childrenKeys = listOf(item.key)
                        ))
                    }
                } else {
                    // Render Collapsed Stack
                    val keys = sortedItems.map { it.key }
                    val previews = sortedItems.take(3).mapNotNull { 
                        val title = it.notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()
                        if (title.isNullOrBlank()) null else title
                    }.joinToString(", ")
                    
                    finalModels.add(NotificationUiModel(
                        key = representative.key,
                        sbn = representative,
                        isGroup = true,
                        count = count,
                        summaryText = previews,
                        childrenKeys = keys
                    ))
                }
            }

            _activeModels.postValue(finalModels)
        } catch (e: SecurityException) {
            Log.e(TAG, "Security Exception", e)
        }
    }
    
    private fun shouldInclude(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName == packageName) return false
        if ((sbn.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY) != 0) return false
        return true
    }
}
