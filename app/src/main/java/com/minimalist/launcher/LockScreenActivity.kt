package com.minimalist.launcher

import android.app.KeyguardManager
import android.content.Context
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.minimalist.launcher.data.GatekeeperGoal
import com.minimalist.launcher.data.GatekeeperRepository
import com.minimalist.launcher.ui.GatekeeperTaskAdapter

/**
 * Custom Lock Screen Activity
 * 
 * Shows over the system lock screen with:
 * - Goal + countdown (tap to edit)
 * - Today's tasks with checkboxes
 * - Trophy room button
 * - Swipe up or button to dismiss
 */
class LockScreenActivity : AppCompatActivity() {

    private lateinit var repository: GatekeeperRepository
    private lateinit var taskAdapter: GatekeeperTaskAdapter
    
    // Views
    private lateinit var goalText: TextView
    private lateinit var countdownText: TextView
    private lateinit var taskRecyclerView: RecyclerView
    private lateinit var victoryText: TextView
    private lateinit var trophyText: TextView
    private lateinit var dismissButton: TextView
    private lateinit var rootLayout: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Show over lock screen
        showOverLockScreen()
        
        setContentView(R.layout.activity_lock_screen)
        
        repository = GatekeeperRepository(this)
        
        setupViews()
        setupTaskAdapter()
        setupClickListeners()
        loadData()
        hideSystemUI()
    }
    
    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        
        // Keep screen on while this activity is showing
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    
    private fun setupViews() {
        rootLayout = findViewById(R.id.lockScreenRoot)
        goalText = findViewById(R.id.lockGoalText)
        countdownText = findViewById(R.id.lockCountdownText)
        taskRecyclerView = findViewById(R.id.lockTaskRecyclerView)
        victoryText = findViewById(R.id.lockVictoryText)
        trophyText = findViewById(R.id.lockTrophyText)
        dismissButton = findViewById(R.id.dismissButton)
    }
    
    private fun setupTaskAdapter() {
        taskAdapter = GatekeeperTaskAdapter(
            onTaskCompleted = { task ->
                repository.completeTask(task.id)
                loadData()
            },
            onTaskUncompleted = { task ->
                repository.uncompleteTask(task.id)
                loadData()
            },
            onTaskDeleted = { task ->
                repository.deleteTask(task.id)
                loadData()
            },
            showCompleted = false
        )
        
        taskRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@LockScreenActivity)
            adapter = taskAdapter
        }
    }
    
    private fun setupClickListeners() {
        // Goal click -> Edit goal
        goalText.setOnClickListener {
            showGoalDialog()
        }
        
        // Trophy room -> Show completed
        trophyText.setOnClickListener {
            showTrophyDialog()
        }
        
        // Dismiss button
        dismissButton.setOnClickListener {
            finish()
            overridePendingTransition(0, android.R.anim.fade_out)
        }
        
        // Add task hint
        findViewById<TextView>(R.id.addTaskHint)?.setOnClickListener {
            showAddTaskDialog()
        }
    }
    
    private fun loadData() {
        // Load goal
        val goal = repository.getGoal()
        if (goal != null) {
            goalText.text = goal.title
            countdownText.text = "${goal.getDaysRemaining()} DAYS LEFT"
            countdownText.visibility = View.VISIBLE
        } else {
            goalText.text = "TAP TO SET GOAL"
            countdownText.visibility = View.GONE
        }
        
        // Load tasks
        val todayTasks = repository.getTodayTasks()
        taskAdapter.submitList(todayTasks)
        
        val completedCount = repository.getCompletedCount()
        
        // Victory state
        if (todayTasks.isEmpty() && completedCount > 0) {
            victoryText.visibility = View.VISIBLE
            taskRecyclerView.visibility = View.GONE
            findViewById<View>(R.id.addTaskHint)?.visibility = View.GONE
        } else if (todayTasks.isEmpty()) {
            victoryText.visibility = View.GONE
            taskRecyclerView.visibility = View.GONE
            findViewById<View>(R.id.addTaskHint)?.visibility = View.VISIBLE
        } else {
            victoryText.visibility = View.GONE
            taskRecyclerView.visibility = View.VISIBLE
            findViewById<View>(R.id.addTaskHint)?.visibility = View.GONE
        }
        
        // Trophy count
        trophyText.text = "🏆 $completedCount completed"
    }
    
    private fun showGoalDialog() {
        val inputLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }
        
        val titleInput = EditText(this).apply {
            hint = "Your goal"
            setHintTextColor(getColor(R.color.secondaryText))
            setTextColor(getColor(R.color.primaryText))
            setText(repository.getGoal()?.title ?: "")
        }
        inputLayout.addView(titleInput)
        
        val daysInput = EditText(this).apply {
            hint = "Days until goal"
            setHintTextColor(getColor(R.color.secondaryText))
            setTextColor(getColor(R.color.primaryText))
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(repository.getGoal()?.getDaysRemaining()?.toString() ?: "100")
        }
        inputLayout.addView(daysInput)
        
        AlertDialog.Builder(this, R.style.MinimalistDialog)
            .setTitle("Set Goal")
            .setView(inputLayout)
            .setPositiveButton("Save") { _, _ ->
                val title = titleInput.text.toString().trim()
                val days = daysInput.text.toString().toIntOrNull() ?: 100
                if (title.isNotEmpty()) {
                    val targetDate = System.currentTimeMillis() + (days.toLong() * 24 * 60 * 60 * 1000)
                    repository.setGoal(GatekeeperGoal(title, targetDate))
                    loadData()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showAddTaskDialog() {
        val taskInput = EditText(this).apply {
            hint = "What needs to be done?"
            setHintTextColor(getColor(R.color.secondaryText))
            setTextColor(getColor(R.color.primaryText))
            setPadding(48, 32, 48, 32)
        }
        
        AlertDialog.Builder(this, R.style.MinimalistDialog)
            .setTitle("Add Task")
            .setView(taskInput)
            .setPositiveButton("Add") { _, _ ->
                val title = taskInput.text.toString().trim()
                if (title.isNotEmpty()) {
                    repository.addTask(title)
                    loadData()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showTrophyDialog() {
        val completed = repository.getCompletedTasksThisWeek()
        if (completed.isEmpty()) {
            Toast.makeText(this, "No completed tasks yet!", Toast.LENGTH_SHORT).show()
            return
        }
        
        val list = completed.joinToString("\n") { "✓ ${it.title}" }
        
        AlertDialog.Builder(this, R.style.MinimalistDialog)
            .setTitle("🏆 This Week")
            .setMessage(list)
            .setPositiveButton("Nice!", null)
            .show()
    }
    
    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    
    override fun onBackPressed() {
        // Prevent back press - must use dismiss button
    }
}
