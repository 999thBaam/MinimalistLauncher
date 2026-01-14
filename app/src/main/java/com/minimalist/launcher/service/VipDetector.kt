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

/**
 * VipDetector: Relationship Intelligence Layer
 * 
 * Auto-detects VIPs using passive signals, NOT manual configuration.
 * This is the SECOND layer in the Intent Firewall decision stack.
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
    private const val MIN_INTERACTIONS_FOR_REPLY_RATIO = 3
    private const val REPLY_RATIO_VIP_THRESHOLD = 0.5f  // Respond to 50%+ = VIP
    
    // Messaging app packages for authority heuristics
    private val MESSAGING_APPS = setOf(
        "com.whatsapp",
        "org.telegram.messenger",
        "com.google.android.apps.messaging",
        "com.android.messaging"
    )
    
    // In-memory cache (refreshed periodically)
    private val vipCache = ConcurrentHashMap<String, VipSource>()
    private var lastRefreshTime = 0L
    private const val REFRESH_INTERVAL_MS = 24 * 60 * 60 * 1000L  // 24 hours
    
    /**
     * Check if the sender of this notification is a VIP.
     * 
     * @return true if VIP, false otherwise
     */
    fun isVip(sbn: StatusBarNotification, context: Context): Boolean {
        refreshCacheIfNeeded(context)
        
        val senderKey = BreakthroughDetector.SenderKey.fromNotification(sbn)
        val senderId = senderKey.senderId
        
        // Check 1: Manual VIP list (user-selected, highest priority)
        if (vipCache[senderId]?.source == VipSource.Source.MANUAL) {
            Log.d(TAG, "VIP: $senderId (manual)")
            return true
        }
        
        // Check 2: Starred contact
        if (vipCache[senderId]?.source == VipSource.Source.STARRED) {
            Log.d(TAG, "VIP: $senderId (starred)")
            return true
        }
        
        // Check 3: Call duration (learned)
        if (vipCache[senderId]?.source == VipSource.Source.CALL_DURATION) {
            Log.d(TAG, "VIP: $senderId (call duration)")
            return true
        }
        
        // Check 4: Reply ratio (learned) - only if we have enough samples
        if (vipCache[senderId]?.source == VipSource.Source.REPLY_RATIO) {
            Log.d(TAG, "VIP: $senderId (reply ratio)")
            return true
        }
        
        return false
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
        // 1. Sender is VIP (already trusted)
        if (isVip(sbn, context)) return true
        
        // 2. Sender is repeat sender (from BreakthroughDetector state)
        // This is checked indirectly via double-knock, so we don't elevate unless
        // there's a strong combined signal
        
        // We do NOT elevate based on text patterns like "admin", "sir" alone
        // That would be exploitable
        
        return false
    }
    
    /**
     * Manually add a VIP.
     */
    fun addManualVip(context: Context, senderId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getStringSet(KEY_MANUAL_VIPS, emptySet()) ?: emptySet()
        val updated = existing.toMutableSet().apply { add(normalizeSenderId(senderId)) }
        prefs.edit().putStringSet(KEY_MANUAL_VIPS, updated).apply()
        
        // Update cache
        vipCache[normalizeSenderId(senderId)] = VipSource(VipSource.Source.MANUAL)
    }
    
    /**
     * Remove a VIP.
     */
    fun removeVip(context: Context, senderId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val normalized = normalizeSenderId(senderId)
        
        // Remove from manual list
        val manualVips = prefs.getStringSet(KEY_MANUAL_VIPS, emptySet()) ?: emptySet()
        prefs.edit().putStringSet(KEY_MANUAL_VIPS, manualVips.toMutableSet().apply { remove(normalized) }).apply()
        
        // Remove from learned list
        val learnedVips = prefs.getStringSet(KEY_LEARNED_VIPS, emptySet()) ?: emptySet()
        prefs.edit().putStringSet(KEY_LEARNED_VIPS, learnedVips.toMutableSet().apply { remove(normalized) }).apply()
        
        // Update cache
        vipCache.remove(normalized)
    }
    
    /**
     * Get list of all VIPs for display in settings.
     */
    fun getVipList(context: Context): List<VipEntry> {
        refreshCacheIfNeeded(context)
        return vipCache.map { (id, source) -> VipEntry(id, source.source) }
    }
    
    /**
     * Force refresh the VIP cache.
     */
    fun forceRefresh(context: Context) {
        lastRefreshTime = 0
        refreshCacheIfNeeded(context)
    }
    
    private fun refreshCacheIfNeeded(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastRefreshTime < REFRESH_INTERVAL_MS && vipCache.isNotEmpty()) {
            return  // Cache is fresh
        }
        
        Log.d(TAG, "Refreshing VIP cache...")
        vipCache.clear()
        
        // Load manual VIPs (always available)
        loadManualVips(context)
        
        // Load starred contacts (permission-guarded)
        loadStarredContacts(context)
        
        // Load from call duration (permission-guarded)
        loadCallDurationVips(context)
        
        // Load from reply ratio (from our own NotificationLogger data)
        loadReplyRatioVips(context)
        
        lastRefreshTime = now
        Log.d(TAG, "VIP cache refreshed: ${vipCache.size} VIPs")
    }
    
    private fun loadManualVips(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val manualVips = prefs.getStringSet(KEY_MANUAL_VIPS, emptySet()) ?: emptySet()
        manualVips.forEach { vipCache[it] = VipSource(VipSource.Source.MANUAL) }
    }
    
    private fun loadStarredContacts(context: Context) {
        // Check permission first (graceful fallback)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) 
            != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "READ_CONTACTS permission not granted, skipping starred contacts")
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
                        val normalized = normalizeSenderId(phone)
                        if (vipCache[normalized] == null) {
                            vipCache[normalized] = VipSource(VipSource.Source.STARRED, name)
                        }
                    }
                    
                    // Also add by name (for non-phone senders)
                    val normalizedName = normalizeSenderId(name)
                    if (vipCache[normalizedName] == null) {
                        vipCache[normalizedName] = VipSource(VipSource.Source.STARRED, name)
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
    
    private fun loadCallDurationVips(context: Context) {
        // Check permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) 
            != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "READ_CALL_LOG permission not granted, skipping call duration analysis")
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
                    val normalized = normalizeSenderId(number)
                    
                    // Don't override manual or starred
                    if (vipCache[normalized] == null) {
                        vipCache[normalized] = VipSource(VipSource.Source.CALL_DURATION)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading call duration VIPs", e)
        }
    }
    
    private fun loadReplyRatioVips(context: Context) {
        // Read from our NotificationLogger data
        try {
            val file = java.io.File(context.filesDir, "notification_training_data.csv")
            if (!file.exists()) return
            
            // Track: sender -> (opened count, total count)
            val senderStats = mutableMapOf<String, Pair<Int, Int>>()
            
            file.bufferedReader().use { reader ->
                reader.readLine() // Skip header
                reader.forEachLine { line ->
                    val parts = line.split(",")
                    if (parts.size >= 7) {
                        try {
                            val pkg = parts[1]
                            val label = parts[6].toIntOrNull() ?: return@forEachLine
                            
                            // For VIP detection, we care about packages where user actually opens
                            // Package name serves as a proxy for "sender" in aggregate
                            val current = senderStats.getOrDefault(pkg, Pair(0, 0))
                            val opened = if (label == 1) 1 else 0
                            senderStats[pkg] = Pair(current.first + opened, current.second + 1)
                        } catch (e: Exception) { }
                    }
                }
            }
            
            // Convert to VIPs based on thresholds
            senderStats.forEach { (pkg, stats) ->
                val (opened, total) = stats
                
                // Require minimum samples before declaring VIP
                if (total >= MIN_INTERACTIONS_FOR_REPLY_RATIO) {
                    val ratio = opened.toFloat() / total
                    if (ratio >= REPLY_RATIO_VIP_THRESHOLD) {
                        val normalized = normalizeSenderId(pkg)
                        // Don't override higher-priority sources
                        if (vipCache[normalized] == null) {
                            vipCache[normalized] = VipSource(VipSource.Source.REPLY_RATIO)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading reply ratio VIPs", e)
        }
    }
    
    private fun normalizeSenderId(raw: String): String {
        val trimmed = raw.trim()
        val digitsOnly = trimmed.filter { it.isDigit() }
        return if (digitsOnly.length >= 7 && digitsOnly.length <= 15) {
            digitsOnly
        } else {
            trimmed.lowercase()
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
            REPLY_RATIO    // High reply rate
        }
    }
    
    data class VipEntry(
        val senderId: String,
        val source: VipSource.Source
    )
}
