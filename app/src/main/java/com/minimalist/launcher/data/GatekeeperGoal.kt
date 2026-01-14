package com.minimalist.launcher.data

import org.json.JSONObject

/**
 * Data class representing the main goal (North Star) in the Gatekeeper system.
 */
data class GatekeeperGoal(
    val title: String,
    val targetDate: Long  // Timestamp of target date
) {
    /**
     * Converts this goal to a JSON object for storage.
     */
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("title", title)
            put("targetDate", targetDate)
        }
    }

    /**
     * Calculates the number of days remaining until the target date.
     */
    fun getDaysRemaining(): Int {
        val now = System.currentTimeMillis()
        val diff = targetDate - now
        return (diff / (24 * 60 * 60 * 1000)).toInt().coerceAtLeast(0)
    }

    companion object {
        /**
         * Creates a GatekeeperGoal from a JSON object.
         */
        fun fromJson(json: JSONObject): GatekeeperGoal {
            return GatekeeperGoal(
                title = json.getString("title"),
                targetDate = json.getLong("targetDate")
            )
        }
    }
}
