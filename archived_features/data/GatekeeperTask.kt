package com.minimalist.launcher.data

import org.json.JSONObject
import java.util.UUID

/**
 * Data class representing a single task in the Gatekeeper system.
 */
data class GatekeeperTask(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * Converts this task to a JSON object for storage.
     */
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("title", title)
            put("completed", completed)
            put("completedAt", completedAt ?: JSONObject.NULL)
            put("createdAt", createdAt)
        }
    }

    companion object {
        /**
         * Creates a GatekeeperTask from a JSON object.
         */
        fun fromJson(json: JSONObject): GatekeeperTask {
            return GatekeeperTask(
                id = json.getString("id"),
                title = json.getString("title"),
                completed = json.getBoolean("completed"),
                completedAt = if (json.isNull("completedAt")) null else json.getLong("completedAt"),
                createdAt = json.getLong("createdAt")
            )
        }
    }
}
