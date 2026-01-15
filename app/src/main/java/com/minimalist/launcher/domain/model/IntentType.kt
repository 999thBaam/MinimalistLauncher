package com.minimalist.launcher.domain.model

/**
 * IntentType: Core classification for the Intent Firewall (Layer 1).
 * 
 * Represents purely the user's INTENT, not urgency or delivery mechanics.
 */
enum class IntentType {
    /**
     * Human-to-human communication.
     * Examples: DMs, Mentions, Replies.
     */
    MESSAGE,

    /**
     * Informational system/app state.
     * Examples: "Storage full", "Upload complete", "Wifi connected".
     */
    STATE,

    /**
     * Requires action (immediate or deferred).
     * Examples: "Bill due", "Verify account", "Update available".
     */
    TASK,

    /**
     * Promotional/Marketing content.
     * Examples: "50% Off", "Buy now", "New Arrival".
     */
    PROMO,

    /**
     * Social engagement bait (non-direct).
     * Examples: "You appeared in search", "Suggested for you".
     */
    SOCIAL
}
