package com.minimalist.launcher.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.minimalist.launcher.LockScreenActivity

/**
 * Listens for screen on/off events to show custom lock screen.
 */
class ScreenReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_ON -> {
                // Check if lock screen feature is enabled
                val prefs = context.getSharedPreferences("minimalist_prefs", Context.MODE_PRIVATE)
                val isLockScreenEnabled = prefs.getBoolean("gatekeeper_lock_screen_enabled", false)
                
                if (isLockScreenEnabled) {
                    // Launch lock screen activity
                    val lockIntent = Intent(context, LockScreenActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                        addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    }
                    context.startActivity(lockIntent)
                }
            }
        }
    }
}
