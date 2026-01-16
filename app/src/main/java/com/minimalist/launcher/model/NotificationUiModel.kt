package com.minimalist.launcher.model

import android.service.notification.StatusBarNotification

/**
 * Represents a Notification item in the Minimalist Launcher UI.
 * Can be a single notification or a stack of multiple notifications from the same app.
 */
data class NotificationUiModel(
    val key: String,
    val sbn: StatusBarNotification,
    val isGroup: Boolean = false,
    val count: Int = 1,
    val summaryText: String = "",
    val childrenKeys: List<String> = emptyList()
)
