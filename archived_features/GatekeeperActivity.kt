package com.minimalist.launcher

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.minimalist.launcher.data.GatekeeperGoal
import com.minimalist.launcher.data.GatekeeperRepository
import com.minimalist.launcher.data.GatekeeperTask
import com.minimalist.launcher.ui.GatekeeperTaskAdapter
import com.minimalist.launcher.ui.StreakVisualizerView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Gatekeeper Activity - Progressive disclosure lock screen pattern.
 * 
 * Three zones:
 * 1. North Star (Top): Main goal + countdown timer
 * 2. Battleground (Center): Today's unchecked tasks
 * 3. Trophy Room (Bottom): Completed tasks with streak visualizer
 */
class GatekeeperActivity : AppCompatActivity() {

    private lateinit var repository: GatekeeperRepository
    
    // Zone 1: North Star
    private lateinit var goalText: TextView
    private lateinit var countdownText: TextView
    private lateinit var editGoalButton: ImageView
    
    // Zone 2: Battleground
    private lateinit var taskRecyclerView: RecyclerView
    private lateinit var victoryState: LinearLayout
    private lateinit var emptyState: LinearLayout
    private lateinit var addTaskHint: TextView
    private lateinit var addTaskFab: FloatingActionButton
    private lateinit var taskAdapter: GatekeeperTaskAdapter
    
    // Zone 3: Trophy Room
    private lateinit var trophyRoomButton: LinearLayout
    private lateinit var trophyRoomText: TextView
    private lateinit var trophyRoomSheet: FrameLayout
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<FrameLayout>
    private lateinit var streakVisualizer: StreakVisualizerView
    private lateinit var completedTasksRecyclerView: RecyclerView
    private lateinit var completedTaskAdapter: GatekeeperTaskAdapter
    
    // Navigation
    private lateinit var backButton: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gatekeeper)
        
        repository = GatekeeperRepository(this)
        
        setupViews()
        setupRecyclerViews()
        setupBottomSheet()
        setupClickListeners()
        
        loadData()
        hideSystemUI()
    }
    
    private fun setupViews() {
        // Zone 1
        goalText = findViewById(R.id.goalText)
        countdownText = findViewById(R.id.countdownText)
        editGoalButton = findViewById(R.id.editGoalButton)
        
        // Zone 2
        taskRecyclerView = findViewById(R.id.taskRecyclerView)
        victoryState = findViewById(R.id.victoryState)
        emptyState = findViewById(R.id.emptyState)
        addTaskHint = findViewById(R.id.addTaskHint)
        addTaskFab = findViewById(R.id.addTaskFab)
        
        // Zone 3
        trophyRoomButton = findViewById(R.id.trophyRoomButton)
        trophyRoomText = findViewById(R.id.trophyRoomText)
        trophyRoomSheet = findViewById(R.id.trophyRoomSheet)
        streakVisualizer = findViewById(R.id.streakVisualizer)
        completedTasksRecyclerView = findViewById(R.id.completedTasksRecyclerView)
        
        // Navigation
        backButton = findViewById(R.id.backButton)
    }
    
    private fun setupRecyclerViews() {
        // Today's tasks adapter
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
            layoutManager = LinearLayoutManager(this@GatekeeperActivity)
            adapter = taskAdapter
        }
        
        // Completed tasks adapter (for trophy room)
        completedTaskAdapter = GatekeeperTaskAdapter(
            onTaskCompleted = { },
            onTaskUncompleted = { task ->
                repository.uncompleteTask(task.id)
                loadData()
            },
            onTaskDeleted = { task ->
                repository.deleteTask(task.id)
                loadData()
            },
            showCompleted = true
        )
        
        completedTasksRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@GatekeeperActivity)
            adapter = completedTaskAdapter
        }
    }
    
    private fun setupBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(trophyRoomSheet)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        bottomSheetBehavior.peekHeight = 0
        bottomSheetBehavior.isHideable = true
    }
    
    private fun setupClickListeners() {
        // Back button
        backButton.setOnClickListener {
            finish()
        }
        
        // Edit goal
        editGoalButton.setOnClickListener {
            showGoalDialog()
        }
        
        // Add task
        addTaskFab.setOnClickListener {
            showAddTaskDialog()
        }
        addTaskHint.setOnClickListener {
            showAddTaskDialog()
        }
        
        // Trophy room toggle
        trophyRoomButton.setOnClickListener {
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            } else {
                trophyRoomSheet.visibility = View.VISIBLE
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }
    
    private fun loadData() {
        loadGoal()
        loadTasks()
        loadTrophyRoom()
    }
    
    private fun loadGoal() {
        val goal = repository.getGoal()
        if (goal != null) {
            goalText.text = goal.title
            val daysRemaining = goal.getDaysRemaining()
            countdownText.text = "$daysRemaining Days Left"
        } else {
            goalText.text = "Set Your Goal"
            countdownText.text = "Tap to configure"
            goalText.setOnClickListener { showGoalDialog() }
        }
    }
    
    private fun loadTasks() {
        val todayTasks = repository.getTodayTasks()
        taskAdapter.submitList(todayTasks)
        
        when {
            todayTasks.isEmpty() && repository.getCompletedCount() > 0 -> {
                // All tasks done - show victory
                taskRecyclerView.visibility = View.GONE
                victoryState.visibility = View.VISIBLE
                emptyState.visibility = View.GONE
            }
            todayTasks.isEmpty() -> {
                // No tasks at all - show empty state
                taskRecyclerView.visibility = View.GONE
                victoryState.visibility = View.GONE
                emptyState.visibility = View.VISIBLE
            }
            else -> {
                // Tasks to do
                taskRecyclerView.visibility = View.VISIBLE
                victoryState.visibility = View.GONE
                emptyState.visibility = View.GONE
            }
        }
    }
    
    private fun loadTrophyRoom() {
        val completedCount = repository.getCompletedCount()
        trophyRoomText.text = "View $completedCount Completed Tasks >"
        
        // Load streak data
        val weeklyData = repository.getWeeklyCompletionData()
        streakVisualizer.setCompletionData(weeklyData)
        
        // Load completed tasks for this week
        val completedTasks = repository.getCompletedTasksThisWeek()
        completedTaskAdapter.submitList(completedTasks)
    }
    
    private fun showGoalDialog() {
        
        val builder = AlertDialog.Builder(this, R.style.MinimalistDialog)
        
        val inputLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }
        
        val titleInput = EditText(this).apply {
            hint = "Goal (e.g., IIT BOMBAY CSE)"
            setHintTextColor(getColor(R.color.secondaryText))
            setTextColor(getColor(R.color.primaryText))
            setText(repository.getGoal()?.title ?: "")
        }
        inputLayout.addView(titleInput)
        
        val dateHint = TextView(this).apply {
            text = "Days until goal:"
            setTextColor(getColor(R.color.secondaryText))
            setPadding(0, 24, 0, 8)
        }
        inputLayout.addView(dateHint)
        
        val daysInput = EditText(this).apply {
            hint = "Number of days"
            setHintTextColor(getColor(R.color.secondaryText))
            setTextColor(getColor(R.color.primaryText))
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(repository.getGoal()?.getDaysRemaining()?.toString() ?: "100")
        }
        inputLayout.addView(daysInput)
        
        builder.setTitle("Set Your North Star")
            .setView(inputLayout)
            .setPositiveButton("Save") { _, _ ->
                val title = titleInput.text.toString().trim()
                val days = daysInput.text.toString().toIntOrNull() ?: 100
                
                if (title.isNotEmpty()) {
                    val targetDate = System.currentTimeMillis() + (days.toLong() * 24 * 60 * 60 * 1000)
                    repository.setGoal(GatekeeperGoal(title, targetDate))
                    loadGoal()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showAddTaskDialog() {
        val inputLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }
        
        val taskInput = EditText(this).apply {
            hint = "Task description"
            setHintTextColor(getColor(R.color.secondaryText))
            setTextColor(getColor(R.color.primaryText))
            requestFocus()
        }
        inputLayout.addView(taskInput)
        
        AlertDialog.Builder(this, R.style.MinimalistDialog)
            .setTitle("Add Task")
            .setView(inputLayout)
            .setPositiveButton("Add") { _, _ ->
                val title = taskInput.text.toString().trim()
                if (title.isNotEmpty()) {
                    repository.addTask(title)
                    loadData()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
        
        // Show keyboard
        taskInput.postDelayed({
            taskInput.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(taskInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }, 200)
    }
    
    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    
    override fun onResume() {
        super.onResume()
        loadData()
    }
}
