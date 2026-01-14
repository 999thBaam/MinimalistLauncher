package com.minimalist.launcher.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.view.View
import android.widget.RemoteViews
import com.minimalist.launcher.GatekeeperActivity
import com.minimalist.launcher.R
import com.minimalist.launcher.data.GatekeeperRepository

/**
 * Gatekeeper Widget Provider
 * 
 * Displays goal, countdown, and today's tasks on home screen or lock screen.
 * - Checkboxes: Toggle task completion directly
 * - Everything else: Opens GatekeeperActivity for editing
 */
class GatekeeperWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE_TASK = "com.minimalist.launcher.TOGGLE_TASK"
        const val EXTRA_TASK_ID = "task_id"
        
        // Task container/checkbox/text IDs for up to 5 tasks
        private val TASK_CONTAINERS = listOf(
            R.id.task1Container, R.id.task2Container, R.id.task3Container,
            R.id.task4Container, R.id.task5Container
        )
        private val TASK_CHECKBOXES = listOf(
            R.id.task1Checkbox, R.id.task2Checkbox, R.id.task3Checkbox,
            R.id.task4Checkbox, R.id.task5Checkbox
        )
        private val TASK_TEXTS = listOf(
            R.id.task1Text, R.id.task2Text, R.id.task3Text,
            R.id.task4Text, R.id.task5Text
        )
        
        /**
         * Request widget update from any context
         */
        fun requestUpdate(context: Context) {
            val intent = Intent(context, GatekeeperWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            context.sendBroadcast(intent)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        when (intent.action) {
            ACTION_TOGGLE_TASK -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
                val repository = GatekeeperRepository(context)
                
                // Toggle task completion
                val task = repository.getAllTasks().find { it.id == taskId }
                if (task != null) {
                    if (task.completed) {
                        repository.uncompleteTask(taskId)
                    } else {
                        repository.completeTask(taskId)
                    }
                }
                
                // Refresh all widgets
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val widgetIds = appWidgetManager.getAppWidgetIds(
                    android.content.ComponentName(context, GatekeeperWidgetProvider::class.java)
                )
                onUpdate(context, appWidgetManager, widgetIds)
            }
            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                // Handle manual refresh request
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val widgetIds = appWidgetManager.getAppWidgetIds(
                    android.content.ComponentName(context, GatekeeperWidgetProvider::class.java)
                )
                onUpdate(context, appWidgetManager, widgetIds)
            }
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val repository = GatekeeperRepository(context)
        val views = RemoteViews(context.packageName, R.layout.widget_gatekeeper)
        
        // Open Gatekeeper intent (used for goal, trophy, empty state)
        val openGatekeeperIntent = Intent(context, GatekeeperActivity::class.java)
        val openGatekeeperPendingIntent = PendingIntent.getActivity(
            context, 0, openGatekeeperIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Set click listeners for goal and trophy room
        views.setOnClickPendingIntent(R.id.goalContainer, openGatekeeperPendingIntent)
        views.setOnClickPendingIntent(R.id.trophyButton, openGatekeeperPendingIntent)
        views.setOnClickPendingIntent(R.id.emptyText, openGatekeeperPendingIntent)
        views.setOnClickPendingIntent(R.id.victoryText, openGatekeeperPendingIntent)
        
        // Load goal
        val goal = repository.getGoal()
        if (goal != null) {
            views.setTextViewText(R.id.widgetGoalText, goal.title)
            views.setTextViewText(R.id.widgetCountdownText, "${goal.getDaysRemaining()} DAYS LEFT")
            views.setViewVisibility(R.id.widgetCountdownText, View.VISIBLE)
        } else {
            views.setTextViewText(R.id.widgetGoalText, "TAP TO SET GOAL")
            views.setViewVisibility(R.id.widgetCountdownText, View.GONE)
        }
        
        // Load today's tasks
        val todayTasks = repository.getTodayTasks()
        val completedCount = repository.getCompletedCount()
        
        // Hide all task containers first
        TASK_CONTAINERS.forEach { views.setViewVisibility(it, View.GONE) }
        views.setViewVisibility(R.id.victoryText, View.GONE)
        views.setViewVisibility(R.id.emptyText, View.GONE)
        
        if (todayTasks.isEmpty() && completedCount > 0) {
            // Victory state - all tasks done
            views.setViewVisibility(R.id.victoryText, View.VISIBLE)
        } else if (todayTasks.isEmpty()) {
            // No tasks - show empty state
            views.setViewVisibility(R.id.emptyText, View.VISIBLE)
        } else {
            // Show up to 5 tasks
            todayTasks.take(5).forEachIndexed { index, task ->
                val containerId = TASK_CONTAINERS[index]
                val checkboxId = TASK_CHECKBOXES[index]
                val textId = TASK_TEXTS[index]
                
                views.setViewVisibility(containerId, View.VISIBLE)
                views.setTextViewText(textId, task.title)
                
                // Checkbox state
                val checkboxRes = if (task.completed) {
                    R.drawable.ic_checkbox_checked
                } else {
                    R.drawable.ic_checkbox_unchecked
                }
                views.setImageViewResource(checkboxId, checkboxRes)
                
                // Checkbox click -> Toggle task
                val toggleIntent = Intent(context, GatekeeperWidgetProvider::class.java).apply {
                    action = ACTION_TOGGLE_TASK
                    putExtra(EXTRA_TASK_ID, task.id)
                }
                val togglePendingIntent = PendingIntent.getBroadcast(
                    context, task.id.hashCode(), toggleIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(checkboxId, togglePendingIntent)
                
                // Task text click -> Open Gatekeeper
                views.setOnClickPendingIntent(textId, openGatekeeperPendingIntent)
            }
        }
        
        // Trophy room button text
        views.setTextViewText(R.id.trophyButton, "View $completedCount completed >")
        
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
