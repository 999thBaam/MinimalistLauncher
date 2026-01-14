package com.minimalist.launcher.data

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Passive Data Logger for On-Device ML Training.
 * 
 * PRIVACY GUARANTEE:
 * - NO notification text (title/body) is logged.
 * - NO user identifiable information is logged.
 * - Features are strictly metadata (Package, Category, Time).
 * - Data is stored locally in private app storage.
 */
object NotificationLogger {

    private const val FILE_NAME = "notification_training_data.csv"
    private val executor = Executors.newSingleThreadExecutor()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    // Interaction Labels
    const val ACTION_DISMISSED = 0   // User swiped away (Negative)
    const val ACTION_OPENED = 1      // User clicked (Positive)
    const val ACTION_BATCHED = 2     // Auto-batched by rule (Neutral/Weak Negative)
    const val ACTION_PROMOTED = 3    // User manually promoted (Strong Positive)
    const val ACTION_DEMOTED = 4     // User manually demoted (Strong Negative)
    
    fun log(context: Context, packageName: String, category: String?, isOngoing: Boolean, actionLabel: Int) {
        executor.execute {
            try {
                val file = File(context.filesDir, FILE_NAME)
                val isNew = !file.exists()
                
                val writer = FileWriter(file, true)
                
                // Header
                if (isNew) {
                    writer.append("timestamp,package,category,hour,day_of_week,is_ongoing,label\n")
                }
                
                // Features
                val now = Date()
                val timestamp = dateFormat.format(now)
                val hour = SimpleDateFormat("H", Locale.US).format(now) // 0-23
                val day = SimpleDateFormat("u", Locale.US).format(now)  // 1-7 (Mon-Sun)
                val cleanCategory = category ?: "null"
                val ongoingFlag = if (isOngoing) "1" else "0"
                
                // Write Row
                // sanitized_package: we keep package name as it's a key feature, but could hash it if needed.
                // For personal on-device use, package name is fine.
                writer.append("$timestamp,$packageName,$cleanCategory,$hour,$day,$ongoingFlag,$actionLabel\n")
                
                writer.flush()
                writer.close()
                
                Log.d("ML_Logger", "Logged: $packageName -> $actionLabel")
                
                // AUTO-TRAINING TRIGGER
                // Check if we hit the threshold (e.g. 100 rows) to retrain
                // For efficiency, maybe check every 10th write?
                if (file.length() > 0) {
                     // Quick approximations: 100 rows ~ 5KB?
                     // Better: check line count occasionally or maintain a counter in prefs
                    val prefs = context.getSharedPreferences("minimalist_ml_prefs", Context.MODE_PRIVATE)
                    var count = prefs.getInt("row_count", 0)
                    count++
                    prefs.edit().putInt("row_count", count).apply()
                    
                    if (count >= 100) {
                        Log.d("TinyML", "Threshold reached ($count). Triggering Training.")
                        com.minimalist.launcher.ml.TinyPersonalizer.train(context)
                        // Reset counter or keep growing logic (e.g. train every 100)
                        // Let's reset to 0 to train in cycles? or incremental? 
                        // Our simple SGD retrains from scratch on full file. 
                        // So we can just set a flag "needs_training" or just run it.
                        // Ideally: Train every 100 new samples.
                         prefs.edit().putInt("row_count", 0).apply()
                    }
                }
                
            } catch (e: Exception) {
                Log.e("ML_Logger", "Failed to log", e)
            }
        }
    }
}
