package com.minimalist.launcher

import android.app.AlertDialog
import android.app.WallpaperManager
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.BatteryManager
import android.app.Dialog
import android.view.Window
import android.widget.TextView
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.minimalist.launcher.data.AppItem
import com.minimalist.launcher.data.AppRepository
import com.minimalist.launcher.databinding.ActivityMainBinding
import com.minimalist.launcher.ui.AppListAdapter
import android.content.pm.PackageManager
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import com.minimalist.launcher.worker.ReminderWorker
import android.Manifest
import androidx.core.app.ActivityCompat
import java.util.concurrent.TimeUnit
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import android.widget.EditText
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.minimalist.launcher.data.GatekeeperGoal
import com.minimalist.launcher.data.GatekeeperRepository
import com.minimalist.launcher.ui.GatekeeperTaskAdapter

/**
 * Minimalist Focus Launcher - Main Activity
 * 
 * "Bottom-Heavy" design with flipper clock header and
 * bottom-anchored app list for comfortable one-handed use.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appRepository: AppRepository
    private lateinit var adapter: AppListAdapter
    
    private var allApps: List<AppItem> = emptyList()
    
    private val prefs by lazy { 
        getSharedPreferences("minimalist_prefs", Context.MODE_PRIVATE) 
    }
    
    // Gatekeeper Repository (for lock screen)
    private lateinit var gatekeeperRepository: GatekeeperRepository
    
    // Custom Lock Screen
    private val screenReceiver = com.minimalist.launcher.service.ScreenReceiver()
    
    // Notification Indicator Receiver
    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateNotificationIndicator()
        }
    }

    private val setDefaultLauncherResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        prefs.edit().putBoolean("has_prompted_default", true).apply()
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadApps()
        }
    }

    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateHeader()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Check auth and onboarding
        val isAuthenticated = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null
        val isOnboardingComplete = prefs.getBoolean("is_onboarding_complete", false)
        
        if (!isAuthenticated) {
            startActivity(Intent(this, PhoneAuthActivity::class.java))
            finish()
            return
        }
        
        if (!isOnboardingComplete) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        appRepository = AppRepository(this)
        gatekeeperRepository = GatekeeperRepository(this)
        
        // Log analytics
        com.minimalist.launcher.data.AnalyticsRepository(this).logLauncherOpened()

        setupRecyclerView()
        setupFastScroller()
        setupSearch()
        
        // Setup Notification Indicator
        binding.notificationIndicator.setOnImportantClickListener {
             openShadowInbox(true)
        }
        
        binding.notificationIndicator.setOnUnimportantClickListener {
             openShadowInbox(false)
        }
        
        // Settings button
        binding.settingsButton.setOnClickListener { view ->
            showSettingsMenu(view)
        }
        
        // Mic button
        binding.micButton.setOnClickListener {
            launchVoiceSearch()
        }
        
        updateHeader()
        loadApps()
        
        // Initial setup
        setBlackLockscreen()
        scheduleDailyReminder()
        requestNotificationPermission()
        
        // Check if we need to show pin tutorial
        binding.root.post { showPinTutorialIfNeeded() }
    }
    
    override fun onResume() {
        super.onResume()
        // Register receivers
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, filter)
        
        registerReceiver(timeReceiver, IntentFilter(Intent.ACTION_TIME_TICK))
        
        // Screen receiver for custom lock screen
        registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_ON))
        
        // Notification receiver
        val notifFilter = IntentFilter(com.minimalist.launcher.service.SmartNotificationListener.ACTION_NOTIFICATION_STATE_CHANGED)
        registerReceiver(notificationReceiver, notifFilter, Context.RECEIVER_EXPORTED)
        
        // Update initial state
        updateNotificationIndicator()
        
        if (!prefs.getBoolean("has_prompted_default", false)) {
            checkAndPromptDefaultLauncher() // Use the correct method name
        }
        
        // Check permissions logic (Declutter, Notification Access)
        checkPermissions()
    }
    
    override fun onPause() {
        super.onPause()
        unregisterReceiver(packageReceiver)
        unregisterReceiver(timeReceiver)
        unregisterReceiver(notificationReceiver)
    }
    
    private fun updateNotificationIndicator() {
        val important = com.minimalist.launcher.service.NotificationBatchManager.getImportantCount()
        val unimportant = com.minimalist.launcher.service.NotificationBatchManager.getUnimportantCount()
        val isUrgent = com.minimalist.launcher.service.NotificationBatchManager.hasUrgent()
        
        binding.notificationIndicator.updateState(important, unimportant, isUrgent)
    }


    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    private fun hideSystemUI() {
        // Immersive Sticky Mode: Hide Status Bar and Nav Bar
        // Swipe from edge to temporarily reveal
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(android.view.WindowInsets.Type.systemBars())
                controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            // Legacy for older Android versions
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN)
        }
    }
    

    
    private fun showPinTutorialIfNeeded() {
        val shouldShowTutorial = prefs.getBoolean("show_pin_tutorial", true)
        val isOnboardingComplete = prefs.getBoolean("is_onboarding_complete", false)
        
        if (shouldShowTutorial && isOnboardingComplete) {
            val overlay = findViewById<View>(R.id.pinTutorialOverlay) ?: return
            val dismissButton = findViewById<View>(R.id.tutorialDismissButton)
            
            overlay.visibility = View.VISIBLE
            overlay.alpha = 0f
            overlay.animate()
                .alpha(1f)
                .setDuration(300)
                .start()
            
            // Function to dismiss the overlay
            val dismissOverlay = {
                // Auto-pin the first app
                val firstApp = allApps.firstOrNull()
                if (firstApp != null && !firstApp.isPinned) {
                    appRepository.pinApp(firstApp.packageName)
                    loadApps()
                    Toast.makeText(this, "📌 ${firstApp.label} pinned!", Toast.LENGTH_SHORT).show()
                }
                
                // Dismiss overlay
                overlay.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction {
                        overlay.visibility = View.GONE
                    }
                    .start()
                
                // Never show again
                prefs.edit().putBoolean("show_pin_tutorial", false).apply()
            }
            
            // Dismiss on button click
            dismissButton?.setOnClickListener { dismissOverlay() }
            
            // Also dismiss on overlay tap (backup)
            overlay.setOnClickListener { dismissOverlay() }
        }
    }
    
    private fun launchVoiceSearch() {
        try {
            val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, 
                    android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Voice search not available", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showSettingsMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add("System Home Settings")
        
        // Smart Notification Toggle
        val isSmartNotifEnabled = prefs.getBoolean("smart_notifications_enabled", true)
        val toggleTitle = if (isSmartNotifEnabled) "Disable Smart Focus" else "Enable Smart Focus"
        popup.menu.add(toggleTitle)
        
        // Lock Screen Toggle
        val isLockScreenEnabled = prefs.getBoolean("gatekeeper_lock_screen_enabled", false)
        val lockScreenTitle = if (isLockScreenEnabled) "Disable Lock Screen" else "Enable Lock Screen"
        popup.menu.add(lockScreenTitle)
        
        // Gatekeeper Mode
        popup.menu.add("Gatekeeper Mode")
        
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "System Home Settings" -> {
                    Toast.makeText(this, "Opening Settings...", Toast.LENGTH_SHORT).show()
                    try {
                        startActivity(Intent(android.provider.Settings.ACTION_HOME_SETTINGS))
                    } catch (e: Exception) {
                        try {
                            startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                        } catch (e2: Exception) {
                            Toast.makeText(this, "Could not open settings", Toast.LENGTH_SHORT).show()
                        }
                    }
                    true
                }
                "Intent Firewall Settings" -> {
                    startActivity(Intent(this, IntentFirewallSettingsActivity::class.java))
                    true
                }
                lockScreenTitle -> {
                    val newState = !isLockScreenEnabled
                    prefs.edit().putBoolean("gatekeeper_lock_screen_enabled", newState).apply()
                    val msg = if (newState) "Lock Screen Enabled 🔒" else "Lock Screen Disabled 🔓"
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    true
                }
                "Gatekeeper Mode" -> {
                    startActivity(Intent(this, GatekeeperActivity::class.java))
                    true
                }
                else -> false
            }
        }
        popup.show()
    }
    
    private fun setBlackLockscreen() {
        try {
            val wallpaperManager = WallpaperManager.getInstance(this)
            val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.BLACK)
            wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    
    private fun checkPermissions() {
        val usageGranted = appRepository.hasUsageStatsPermission()
        val notificationListenerGranted = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        
        if (!usageGranted) {
            AlertDialog.Builder(this, R.style.MinimalistDialog)
                .setTitle("Enable Digital Declutter")
                .setMessage("To detect and hide unused apps, Minimalist Launcher needs usage access permission.\n\nApps you haven't used in 30 days will fade out.")
                .setPositiveButton("Grant Access") { _, _ ->
                    try {
                        startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    } catch (e: Exception) {}
                }
                .setNegativeButton("No Thanks", null)
                .show()
        } else if (!notificationListenerGranted) {
            // Ask for Notification Access for Smart Notifications
            AlertDialog.Builder(this, R.style.MinimalistDialog)
                .setTitle("Enable Smart Notifications")
                .setMessage("To filter unimportant notifications and batch them, allow Minimalist Launcher to access notifications.\n\nWe sort them locally on your device.")
                .setPositiveButton("Grant Access") { _, _ ->
                    try {
                        startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    } catch (e: Exception) {}
                }
                .setNegativeButton("Later", null)
                .show()
        }
    }

    private fun setupRecyclerView() {
        adapter = AppListAdapter(
            onAppClick = { app -> launchApp(app) },
            onUninstallClick = { app -> uninstallApp(app) },
            onAppLongClick = { app -> showAppOptionsDialog(app) }
        )
        
        binding.appRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
            setHasFixedSize(true)
        }
    }
    
    private fun showAppOptionsDialog(app: AppItem) {
        val options = mutableListOf<String>()
        val pinAction = if (app.isPinned) "Unpin App" else "Pin to Top"
        options.add(pinAction)
        options.add("Uninstall")
        
        AlertDialog.Builder(this, R.style.MinimalistDialog)
            .setTitle(app.label)
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> { // Pin/Unpin
                        if (!app.isPinned) {
                            val pinnedCount = adapter.currentList.count { it.isPinned }
                            if (pinnedCount >= 3) {
                                Toast.makeText(this, "Limit 3 pinned apps", Toast.LENGTH_SHORT).show()
                                return@setItems
                            }
                            appRepository.pinApp(app.packageName)
                        } else {
                            appRepository.unpinApp(app.packageName)
                        }
                        loadApps()
                    }
                    1 -> uninstallApp(app)
                }
            }
            .show()
    }
    
    private fun checkAndPromptDefaultLauncher() {
        if (isDefaultLauncher()) return
        
        val hasPrompted = prefs.getBoolean("has_prompted_default", false)
        if (hasPrompted) return
        
        AlertDialog.Builder(this, R.style.MinimalistDialog)
            .setTitle("Set as Home Screen")
            .setMessage("To enable digital minimalism, set this app as your default launcher.\n\nYou can always change this back in Settings.")
            .setPositiveButton("Set as Default") { _, _ ->
                openLauncherPicker()
            }
            .setNegativeButton("Later") { dialog, _ ->
                prefs.edit().putBoolean("has_prompted_default", true).apply()
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun openLauncherPicker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
            if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                setDefaultLauncherResult.launch(intent)
                return
            }
        }
        
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        prefs.edit().putBoolean("has_prompted_default", true).apply()
    }
    
    private fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = packageManager.resolveActivity(intent, 0)
        return resolveInfo?.activityInfo?.packageName == packageName
    }



    private fun setupSearch() {
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterApps(s?.toString() ?: "")
            }
        })
    }

    private fun loadApps() {
        allApps = appRepository.getInstalledApps()
        filterApps(binding.searchEditText.text?.toString() ?: "")
    }

    private fun filterApps(query: String) {
        val filteredApps = if (query.isBlank()) {
            allApps
        } else {
            // Filter apps that contain the query
            val matches = allApps.filter { 
                it.label.contains(query, ignoreCase = true) 
            }
            
            // Sort: apps STARTING with query first, then others alphabetically
            matches.sortedWith(
                compareByDescending<AppItem> { it.isPinned }
                    .thenByDescending { it.label.startsWith(query, ignoreCase = true) }
                    .thenBy { it.label.lowercase() }
            )
        }
        
        adapter.submitList(filteredApps)
        
        // Show/hide empty state
        binding.emptyText.visibility = if (filteredApps.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun launchApp(app: AppItem) {
        appRepository.getLaunchIntent(app.packageName)?.let { intent ->
            startActivity(intent)
        }
    }
    
    private fun uninstallApp(app: AppItem) {
        Toast.makeText(this, "Opening App Info for ${app.label}", Toast.LENGTH_SHORT).show()
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:${app.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open App Info: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun updateHeader() {
        // Update flipper clock
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        binding.flipperClock.setTime(hour, minute)

        // Update date (below flipper clock)
        val dateFormat = SimpleDateFormat("EEEE d", Locale.getDefault())
        binding.dateText.text = dateFormat.format(Date()).uppercase()
    }



    // Override back press to prevent exiting the launcher
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Clear search and scroll to top instead of exiting
        if (binding.searchEditText.text?.isNotEmpty() == true) {
            binding.searchEditText.text?.clear()
        } else {
            binding.appRecyclerView.smoothScrollToPosition(0)
        }
    }

    private fun scheduleDailyReminder() {
        // DISABLED: User reported annoyance. Cancelling all reminders for now.
        WorkManager.getInstance(this).cancelUniqueWork("DailyReminder")
        
        /* 
        // For testing: Schedule periodic reminder every 15 minutes (WorkManager minimum)
        val reminderRequest = PeriodicWorkRequestBuilder<ReminderWorker>(15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "DailyReminder",
            ExistingPeriodicWorkPolicy.REPLACE, // Replace to apply new interval
            reminderRequest
        )
        
        // Also trigger one immediately for testing
        val immediateRequest = androidx.work.OneTimeWorkRequestBuilder<ReminderWorker>()
            .build()
        WorkManager.getInstance(this).enqueue(immediateRequest)
        */
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    private fun openShadowInbox(showImportant: Boolean) {
        val intent = Intent(this, ShadowInboxActivity::class.java).apply {
            putExtra("SHOW_IMPORTANT", showImportant)
        }
        startActivity(intent)
    }

    private fun setupFastScroller() {
        binding.fastScroller.setOnSectionListener { section ->
            val apps = adapter.currentList
            var index = -1
            
            if (section == "#") {
                index = 0
            } else {
                // Find first app starting with this letter
                index = apps.indexOfFirst { 
                    it.label.startsWith(section, ignoreCase = true) 
                }
                
                // If not found, find the insertion point
                if (index == -1) {
                    index = apps.indexOfFirst { 
                        it.label.compareTo(section, ignoreCase = true) > 0 
                    }
                }
            }

            if (index != -1) {
                (binding.appRecyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(index, 0)
            }
        }
    }
}
