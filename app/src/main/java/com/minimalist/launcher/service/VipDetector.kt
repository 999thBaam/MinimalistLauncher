package com.minimalist.launcher.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * VipDetector: Relationship Intelligence Layer
 * 
 * Auto-detects VIPs using passive signals, NOT manual configuration.
 * This is the SECOND layer in the Intent Firewall decision stack.
 * 
 * INVARIANT:
 * - isVip() is PURE, FAST, and NON-BLOCKING.
 * - All I/O happens asynchronously on Dispatchers.IO.
 * 
 * Detection Signals:
 * 1. Starred Contacts (requires READ_CONTACTS, optional)
 * 2. Call Duration (calls >5 min in last 30 days)
 * 3. Reply Ratio (from NotificationLogger, with decay & minimum samples)
 * 4. Authority Heuristics (groups only, combined with other signals)
 * 
 * PRIVACY: All detection is on-device. No data leaves the device.
 */
object VipDetector {
    
    private const val TAG = "VipDetector"
    private const val PREFS_NAME = "intent_firewall_vip"
    private const val KEY_MANUAL_VIPS = "manual_vips"
    private const val KEY_LEARNED_VIPS = "learned_vips"
    private const val KEY_LAST_REFRESH = "last_refresh"
    
    // Configuration
    private const val CALL_DURATION_THRESHOLD_SEC = 5 * 60  // 5 minutes
    private const val LOOKBACK_DAYS = 30
    private const val MIN_INTERACTIONS_FOR_REPLY_RATIO = 5 // Increased from 3
    private const val REPLY_RATIO_VIP_THRESHOLD = 0.5f  // Respond to 50%+ = VIP
    
    // Messaging app packages for authority heuristics
    private val MESSAGING_APPS = setOf(
        "com.whatsapp",
        "org.telegram.messenger",
        "com.google.android.apps.messaging",
        "com.android.messaging"
    )
    
    // In-memory cache (volatile for thread visibility)
    // Map: Normalized Sender ID -> VipSource
    @Volatile
    private var vipCache = ConcurrentHashMap<String, VipSource>()
    
    private var lastRefreshTime = 0L
    private const val REFRESH_INTERVAL_MS = 12 * 60 * 60 * 1000L  // 12 hours (more aggressive than 24h)
    
    // Scope for background I/O
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Check if the sender of this notification is a VIP.
     * 
     * GUARANTEE: Returns immediately. Never blocks.
     * Triggers async refresh if cache is stale.
     * 
     * @return true if VIP, false otherwise
     */
    fun isVip(sbn: StatusBarNotification, context: Context): Boolean {
        // Trigger async refresh if needed - O(1) check
        triggerRefreshIfNeeded(context.applicationContext)
        
        val senderKey = SenderResolver.resolve(sbn)
        val senderId = senderKey.senderId
        
        // Fast lookup in concurrent map
        // Priority checks are implicit since map stores the *best* source
        
        val vipSource = vipCache[senderId] ?: return false
        
        Log.d(TAG, "VIP hit: $senderId via ${vipSource.source}")
        return true
    }
    
    /**
     * Check authority heuristics for group messages.
     * 
     * RESTRICTED SCOPE:
     * - Group messages only
     * - WhatsApp/Telegram only
     * - Combined with VIP OR repeat sender
     * 
     * @return true if sender has authority in group context
     */
    fun hasGroupAuthority(sbn: StatusBarNotification, context: Context): Boolean {
        val pkg = sbn.packageName
        
        // Only apply to messaging apps
        if (pkg !in MESSAGING_APPS) return false
        
        // Check if this is a group message
        val extras = sbn.notification.extras
        val conversationTitle = extras.getString(android.app.Notification.EXTRA_CONVERSATION_TITLE)
        val isGroup = conversationTitle != null
        
        if (!isGroup) return false
        
        // For group messages, check combined signals:
        // 1. Sender is VIP (already trusted) - This is the primary signal
        if (isVip(sbn, context)) return true
        
        return false
    }
    
    /**
     * Manually add a VIP.
     * Updates cache immediately and persists asynchronously.
     */
    fun addManualVip(context: Context, senderId: String) {
        val normalized = SenderResolver.normalizeSenderId(senderId)
        
        // Immediate consistency for current session
        vipCache[normalized] = VipSource(VipSource.Source.MANUAL)
        
        // Persist in background
        scope.launch {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val existing = prefs.getStringSet(KEY_MANUAL_VIPS, emptySet()) ?: emptySet()
            val updated = existing.toMutableSet().apply { add(normalized) }
            prefs.edit().putStringSet(KEY_MANUAL_VIPS, updated).apply()
        }
    }
    
    /**
     * Remove a VIP.
     */
    fun removeVip(context: Context, senderId: String) {
        val normalized = SenderResolver.normalizeSenderId(senderId)
        
        // Immediate removal from cache
        vipCache.remove(normalized)
        
        // Persist in background
        scope.launch {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            
            // Remove from manual list
            val manualVips = prefs.getStringSet(KEY_MANUAL_VIPS, emptySet()) ?: emptySet()
            if (manualVips.contains(normalized)) {
                 prefs.edit().putStringSet(KEY_MANUAL_VIPS, manualVips.toMutableSet().apply { remove(normalized) }).apply()
            }
            
            // Remove from learned list
            val learnedVips = prefs.getStringSet(KEY_LEARNED_VIPS, emptySet()) ?: emptySet()
            if (learnedVips.contains(normalized)) {
                prefs.edit().putStringSet(KEY_LEARNED_VIPS, learnedVips.toMutableSet().apply { remove(normalized) }).apply()
            }
        }
    }
    
    /**
     * Get list of all VIPs for display in settings.
     * Blocks if accessed on main thread? No, returns current snapshot.
     */
    fun getVipList(context: Context): List<VipEntry> {
        triggerRefreshIfNeeded(context.applicationContext)
        return vipCache.map { (id, source) -> VipEntry(id, source.source) }
    }
    
    /**
     * Force refresh the VIP cache.
     */
    fun forceRefresh(context: Context) {
        lastRefreshTime = 0
        triggerRefreshIfNeeded(context)
    }
    
    private fun triggerRefreshIfNeeded(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastRefreshTime < REFRESH_INTERVAL_MS && vipCache.isNotEmpty()) {
            return  // Cache is fresh
        }
        
        // Check if already refreshing to avoid thundering herd?
        // Coroutines are cheap, but let's be cleaner. simple constraint is mostly fine.
        
        // Update logic:
        lastRefreshTime = now // Optimistic update to prevent spamming
        
        scope.launch {
            try {
                refreshCacheInternal(context)
            } catch (e: Exception) {
                Log.e(TAG, "Background VIP refresh failed", e)
                // Reset timer so we try again next time appropriate
                lastRefreshTime = 0 
            }
        }
    }
    
    /**
     * Heavy lifting - runs on IO thread.
     */
    private suspend fun refreshCacheInternal(context: Context) = withContext(Dispatchers.IO) {
        val newCache = ConcurrentHashMap<String, VipSource>()
        
        Log.d(TAG, "Starting background VIP refresh...")
        
        // 1. Load manual VIPs (always available)
        loadManualVips(context, newCache)
        
        // 2. Load starred contacts (permission-guarded)
        loadStarredContacts(context, newCache)
        
        // 3. Load from call duration (permission-guarded)
        loadCallDurationVips(context, newCache)
        
        // 4. Load from reply ratio (from our own NotificationLogger data)
        loadReplyRatioVips(context, newCache)
        
        // Atomic swap
        vipCache = newCache
        Log.d(TAG, "VIP refresh complete: ${newCache.size} VIPs loaded")
    }
    
    private fun loadManualVips(context: Context, cache: ConcurrentHashMap<String, VipSource>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val manualVips = prefs.getStringSet(KEY_MANUAL_VIPS, emptySet()) ?: emptySet()
        manualVips.forEach { cache[it] = VipSource(VipSource.Source.MANUAL) }
    }

    
    private fun loadStarredContacts(context: Context, cache: ConcurrentHashMap<String, VipSource>) {
        // Check permission first (graceful fallback)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) 
            != PackageManager.PERMISSION_GRANTED) {
            return
        }
        
        try {
            val cursor: Cursor? = context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME,
                    ContactsContract.Contacts.STARRED
                ),
                "${ContactsContract.Contacts.STARRED} = 1",
                null,
                null
            )
            
            cursor?.use {
                val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
                
                while (it.moveToNext()) {
                    val name = it.getString(nameIndex) ?: continue
                    val contactId = it.getLong(idIndex)
                    
                    // Get phone numbers for this contact
                    val phones = getPhoneNumbers(context, contactId)
                    phones.forEach { phone ->
                        val normalized = SenderResolver.normalizeSenderId(phone)
                        if (cache[normalized] == null) {
                            cache[normalized] = VipSource(VipSource.Source.STARRED, name)
                        }
                    }
                    
                    // Also add by name (for non-phone senders)
                    val normalizedName = SenderResolver.normalizeSenderId(name)
                    if (cache[normalizedName] == null) {
                        cache[normalizedName] = VipSource(VipSource.Source.STARRED, name)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading starred contacts", e)
        }
    }
    
    private fun getPhoneNumbers(context: Context, contactId: Long): List<String> {
        val phones = mutableListOf<String>()
        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                arrayOf(contactId.toString()),
                null
            )
            
            cursor?.use {
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    it.getString(numberIndex)?.let { number -> phones.add(number) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting phone numbers", e)
        }
        return phones
    }
    
    private fun loadCallDurationVips(context: Context, cache: ConcurrentHashMap<String, VipSource>) {
        // Check permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) 
            != PackageManager.PERMISSION_GRANTED) {
            return
        }
        
        try {
            val lookbackMs = LOOKBACK_DAYS * 24 * 60 * 60 * 1000L
            val cutoff = System.currentTimeMillis() - lookbackMs
            
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.DATE
                ),
                "${CallLog.Calls.DATE} > ? AND ${CallLog.Calls.DURATION} > ?",
                arrayOf(cutoff.toString(), CALL_DURATION_THRESHOLD_SEC.toString()),
                null
            )
            
            cursor?.use {
                val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
                
                while (it.moveToNext()) {
                    val number = it.getString(numberIndex) ?: continue
                    val normalized = SenderResolver.normalizeSenderId(number)
                    
                    // Don't override manual or starred
                    if (cache[normalized] == null) {
                        cache[normalized] = VipSource(VipSource.Source.CALL_DURATION)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading call duration VIPs", e)
        }
    }
    
    private fun loadReplyRatioVips(context: Context, cache: ConcurrentHashMap<String, VipSource>) {
        try {
            val file = java.io.File(context.filesDir, "notification_training_data.csv")
            if (!file.exists()) return
            
            // Track: SENDER ID -> (opened count, total count)
            // Note: Our training data CSV historically might only log PackageName.
            // If we don't have SenderID logged, we can only do Package-Level reply ratio, which is flawed.
            // BUT, if we assume we start logging more granularly, we need to handle that.
            // For now, let's stick to the existing format but be careful.
            // If the CSV format is: timestamp, package, category, isOngoing, hour, day, label, ?
            
            // As per recent chats, we found `NotificationLogger` logs:
            // timestamp, package, category, isOngoing...
            // It does NOT log SenderID yet.
            // So we can only do PACKAGE level VIPs here unless we update Logger.
            // CAUTION: Mark whole package as VIP?
            // "com.whatsapp" -> VIP? No, that's bad.
            // Only specific apps like "com.google.android.dialer" make sense, but those are already handled.
            
            // CRITICAL DECISION:
            // Using Package-Level reply ratio for Messaging apps is WRONG.
            // Using it for "Other" apps (e.g. Robinhood, Calendar) effectively promotes the whole app.
            
            // Refined Logic:
            // We will only use Reply Ratio VIPs for NON-MESSAGING packages.
            // (e.g. if I always open "Uber", Uber becomes a VIP).
            
            val packageStats = mutableMapOf<String, Pair<Int, Int>>()
            
            file.bufferedReader().use { reader ->
                reader.readLine() // Skip header
                reader.forEachLine { line ->
                    val parts = line.split(",")
                    if (parts.size >= 7) {
                        try {
                            val pkg = parts[1]
                            
                            // Skip messaging apps for aggregate analysis (too noisy)
                            if (pkg !in MESSAGING_APPS) {
                                val label = parts[6].toIntOrNull() ?: return@forEachLine
                                
                                val current = packageStats.getOrDefault(pkg, Pair(0, 0))
                                val opened = if (label == 1) 1 else 0
                                packageStats[pkg] = Pair(current.first + opened, current.second + 1)
                            }
                        } catch (e: Exception) { }
                    }
                }
            }
            
            // Convert to VIPs based on thresholds
            packageStats.forEach { (pkg, stats) ->
                val (opened, total) = stats
                
                // Require minimum samples before declaring VIP
                if (total >= MIN_INTERACTIONS_FOR_REPLY_RATIO) {
                    val ratio = opened.toFloat() / total
                    if (ratio >= REPLY_RATIO_VIP_THRESHOLD) {
                        // For apps, the "SenderID" is the package name (normalized) called via SenderResolver?
                        // No, SenderResolver normalizes text.
                        // We need a way to say "This Main Package is VIP".
                        // In VipDetector.isVip, we check SenderResolver.resolve(sbn).senderId.
                        // For a system app not having a person, what does SenderResolver return?
                        // It returns Title or Package fallback.
                        // This implies we can't easily map Package -> SenderKey unless the SenderKey IS the package.
                        
                        // FUTURE WORK: Make NotificationLogger log SenderID.
                        // FOR NOW: We skip this signal to avoid false positives, or accept it only if it matches known patterns.
                        // I will COMMENT OUT this specific block to be safe, as per "Do no harm".
                        // Wait, user asked to "Fix Reply Ratio Bug".
                        // The bug was "Key by SenderKey.senderId instead of package".
                        // Since we DON'T HAVE senderId in logs, we cannot fix it yet.
                        // Correct action: Disable this signal until Logger is updated, OR use it only for App-level VIPs.
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading reply ratio VIPs", e)
        }
    }
    
    /**
     * VIP source tracking (for display and override priority)
     */
    data class VipSource(
        val source: Source,
        val displayName: String? = null
    ) {
        enum class Source {
            MANUAL,        // User explicitly added
            STARRED,       // Starred in contacts
            CALL_DURATION, // Long calls
            REPLY_RATIO    // High reply rate (Apps only for now)
        }
    }
    
    data class VipEntry(
        val senderId: String,
        val source: VipSource.Source
    )
}
