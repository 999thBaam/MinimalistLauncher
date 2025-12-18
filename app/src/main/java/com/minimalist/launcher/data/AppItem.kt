package com.minimalist.launcher.data

/**
 * Lightweight data class for app representation.
 * Intentionally excludes icon/drawable to enforce text-only display.
 */
data class AppItem(
    val label: String,        // Display name (e.g., "WhatsApp")
    val packageName: String   // Package ID (e.g., "com.whatsapp")
)
