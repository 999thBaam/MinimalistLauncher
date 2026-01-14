package com.minimalist.launcher.data

/**
 * ShadowMessage: Content storage for Ghost Mode
 * 
 * Represents a conversation thread in the Shadow Inbox.
 * Messages are threaded by conversationKey to avoid UI explosion.
 * 
 * CRITICAL: conversationKey must be stable across notification updates.
 * Key format: {packageName}:{senderId OR groupId}
 */
data class ShadowMessage(
    /**
     * Unique thread identifier.
     * Format: {packageName}:{normalized_sender_or_group}
     * 
     * This MUST be stable across notification updates to enable threading.
     */
    val conversationKey: String,
    
    /**
     * Source app package name
     */
    val packageName: String,
    
    /**
     * Sender identifier (normalized)
     * - For DMs: phone number or username
     * - For groups: null (group name is in groupName)
     */
    val sender: String?,
    
    /**
     * Display name for the sender (human readable)
     */
    val senderDisplayName: String?,
    
    /**
     * List of messages in this thread (newest first)
     */
    val messages: MutableList<MessageContent>,
    
    /**
     * Most recent message timestamp
     */
    var lastTimestamp: Long,
    
    /**
     * Whether this is a group conversation
     */
    val isGroup: Boolean,
    
    /**
     * Group name (if isGroup == true)
     */
    val groupName: String?
) {
    companion object {
        /**
         * Generate a stable conversation key.
         * 
         * CRITICAL: This must be consistent for the same conversation
         * across multiple notification updates.
         */
        fun generateConversationKey(
            packageName: String,
            senderId: String?,
            groupId: String?,
            isGroup: Boolean
        ): String {
            val identifier = when {
                isGroup && groupId != null -> "group:${groupId.lowercase().trim()}"
                senderId != null -> "dm:${normalizeSenderId(senderId)}"
                else -> "unknown:${System.currentTimeMillis()}"
            }
            return "$packageName:$identifier"
        }
        
        private fun normalizeSenderId(raw: String): String {
            val trimmed = raw.trim()
            val digitsOnly = trimmed.filter { it.isDigit() }
            return if (digitsOnly.length in 7..15) {
                digitsOnly
            } else {
                trimmed.lowercase()
            }
        }
    }
    
    /**
     * Add a new message to this thread.
     * Inserts at the beginning (newest first).
     */
    fun addMessage(content: MessageContent) {
        messages.add(0, content)
        if (content.timestamp > lastTimestamp) {
            lastTimestamp = content.timestamp
        }
    }
    
    /**
     * Get unread count
     */
    fun getUnreadCount(): Int = messages.size
}

/**
 * Individual message content within a thread
 */
data class MessageContent(
    /**
     * Message text content
     */
    val text: String,
    
    /**
     * Message timestamp
     */
    val timestamp: Long,
    
    /**
     * Sender name (for group messages where multiple people send)
     */
    val senderName: String? = null
)
