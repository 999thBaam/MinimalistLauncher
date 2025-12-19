package com.minimalist.launcher.data

/**
 * Lightweight data class for app representation.
 * Intentionally excludes icon/drawable to enforce text-only display.
 */
data class AppItem(
    val label: String,        // Display name (e.g., "WhatsApp")
    val packageName: String,  // Package ID (e.g., "com.whatsapp")
    val isUnused: Boolean = false, // Digital Decluttering: true if unused for 30+ days
    val isPinned: Boolean = false, // User pinned app to top
    val lastUsedTime: Long = 0     // Timestamp of last usage
)
