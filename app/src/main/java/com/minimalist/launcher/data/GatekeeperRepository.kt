package com.minimalist.launcher.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Repository for managing Gatekeeper tasks and goals.
 * Uses SharedPreferences with JSON serialization for persistence.
 */
class GatekeeperRepository(private val context: Context) {

    private val prefs by lazy {
        context.getSharedPreferences("gatekeeper_prefs", Context.MODE_PRIVATE)
    }

    // ==================== Goal Operations ====================

    /**
     * Gets the current goal, or null if not set.
     */
    fun getGoal(): GatekeeperGoal? {
        val json = prefs.getString("goal", null) ?: return null
        return try {
            GatekeeperGoal.fromJson(JSONObject(json))
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Sets or updates the current goal.
     */
    fun setGoal(goal: GatekeeperGoal) {
        prefs.edit().putString("goal", goal.toJson().toString()).apply()
    }

    /**
     * Clears the current goal.
     */
    fun clearGoal() {
        prefs.edit().remove("goal").apply()
    }

    // ==================== Task Operations ====================

    /**
     * Gets all tasks (both completed and uncompleted).
     */
    fun getAllTasks(): List<GatekeeperTask> {
        val json = prefs.getString("tasks", null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                GatekeeperTask.fromJson(array.getJSONObject(i))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Gets only today's uncompleted tasks.
     */
    fun getTodayTasks(): List<GatekeeperTask> {
        val startOfDay = getStartOfDay()
        return getAllTasks()
            .filter { !it.completed && it.createdAt >= startOfDay }
            .sortedByDescending { it.createdAt }
    }

    /**
     * Gets completed tasks from this week.
     */
    fun getCompletedTasksThisWeek(): List<GatekeeperTask> {
        val startOfWeek = getStartOfWeek()
        return getAllTasks()
            .filter { it.completed && (it.completedAt ?: 0) >= startOfWeek }
            .sortedByDescending { it.completedAt }
    }

    /**
     * Gets the count of completed tasks.
     */
    fun getCompletedCount(): Int {
        return getAllTasks().count { it.completed }
    }

    /**
     * Adds a new task.
     */
    fun addTask(title: String): GatekeeperTask {
        val task = GatekeeperTask(title = title)
        val tasks = getAllTasks().toMutableList()
        tasks.add(task)
        saveTasks(tasks)
        return task
    }

    /**
     * Marks a task as completed.
     */
    fun completeTask(taskId: String) {
        val tasks = getAllTasks().map { task ->
            if (task.id == taskId) {
                task.copy(completed = true, completedAt = System.currentTimeMillis())
            } else {
                task
            }
        }
        saveTasks(tasks)
    }

    /**
     * Marks a task as uncompleted.
     */
    fun uncompleteTask(taskId: String) {
        val tasks = getAllTasks().map { task ->
            if (task.id == taskId) {
                task.copy(completed = false, completedAt = null)
            } else {
                task
            }
        }
        saveTasks(tasks)
    }

    /**
     * Deletes a task.
     */
    fun deleteTask(taskId: String) {
        val tasks = getAllTasks().filter { it.id != taskId }
        saveTasks(tasks)
    }

    /**
     * Gets completion data for the last 7 days (for streak visualizer).
     * Returns a map of day offset (0 = today, 6 = 6 days ago) to completion count.
     */
    fun getWeeklyCompletionData(): Map<Int, Int> {
        val result = mutableMapOf<Int, Int>()
        val now = System.currentTimeMillis()
        val dayMs = 24 * 60 * 60 * 1000L

        for (i in 0..6) {
            result[i] = 0
        }

        getAllTasks().filter { it.completed && it.completedAt != null }.forEach { task ->
            val completedAt = task.completedAt!!
            val daysAgo = ((now - completedAt) / dayMs).toInt()
            if (daysAgo in 0..6) {
                result[daysAgo] = (result[daysAgo] ?: 0) + 1
            }
        }

        return result
    }

    // ==================== Private Helpers ====================

    private fun saveTasks(tasks: List<GatekeeperTask>) {
        val array = JSONArray()
        tasks.forEach { array.put(it.toJson()) }
        prefs.edit().putString("tasks", array.toString()).apply()
    }

    private fun getStartOfDay(): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    private fun getStartOfWeek(): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }
}
