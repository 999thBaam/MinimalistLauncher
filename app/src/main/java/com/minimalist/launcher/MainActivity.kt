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

        appRepository = AppRepository(this)
        setupRecyclerView()
        setupSearch()
        loadApps()
        
        // Check auth and onboarding status
        val isAuthenticated = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null
        val isOnboardingComplete = prefs.getBoolean("is_onboarding_complete", false)
        
        // Flow: Auth -> Onboarding -> Main
        if (!isAuthenticated) {
            // Not authenticated - go to phone auth first
            startActivity(Intent(this, PhoneAuthActivity::class.java))
            finish()
            return
        }
        
        if (!isOnboardingComplete) {
            // Authenticated but onboarding not done
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        
        // Log launcher opened for analytics
        com.minimalist.launcher.data.AnalyticsRepository(this).logLauncherOpened()
        
        // Initial setup sequence
        registerPackageReceiver()
        setBlackLockscreen()
        scheduleDailyReminder()
        requestNotificationPermission()
        setupFastScroller()
        
        // Initial header update
        updateHeader()
        
        // Settings button click -> Show Menu
        binding.settingsButton.setOnClickListener { view ->
            showSettingsMenu(view)
        }
        
        // Mic button for voice search (optional)
        binding.micButton.setOnClickListener {
            launchVoiceSearch()
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
        if (!appRepository.hasUsageStatsPermission()) {
            AlertDialog.Builder(this, R.style.MinimalistDialog)
                .setTitle("Enable Digital Declutter")
                .setMessage("To detect and hide unused apps, Minimalist Launcher needs usage access permission.\n\nApps you haven't used in 30 days will fade out.")
                .setPositiveButton("Grant Access") { _, _ ->
                    try {
                        startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    } catch (e: Exception) {
                         // Ignore
                    }
                }
                .setNegativeButton("No Thanks", null)
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

    override fun onDestroy() {
        super.onDestroy()
        unregisterPackageReceiver()
    }

    override fun onResume() {
        super.onResume()
        updateHeader()
        registerTimeReceiver()
        
        // Clear search query to reset view on return
        if (binding.searchEditText.text?.isNotEmpty() == true) {
            binding.searchEditText.text?.clear()
        }
        
        // Always refresh list on resume
        loadApps()
        
        if (!appRepository.hasUsageStatsPermission()) {
             checkPermissions()
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterTimeReceiver()
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
            allApps.filter { 
                it.label.contains(query, ignoreCase = true) 
            }
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
        
        // Update status bar time (small text)
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        binding.statusTimeText.text = timeFormat.format(Date())

        // Update date (below flipper clock)
        val dateFormat = SimpleDateFormat("EEEE d", Locale.getDefault())
        binding.dateText.text = dateFormat.format(Date()).uppercase()

        // Update battery percentage
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        binding.batteryText.text = "$batteryLevel%"
    }

    private fun registerPackageReceiver() {
        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, packageFilter)
    }

    private fun unregisterPackageReceiver() {
        try {
            unregisterReceiver(packageReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver not registered, ignore
        }
    }

    private fun registerTimeReceiver() {
        val timeFilter = IntentFilter(Intent.ACTION_TIME_TICK)
        registerReceiver(timeReceiver, timeFilter)
    }

    private fun unregisterTimeReceiver() {
        try {
            unregisterReceiver(timeReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver not registered, ignore
        }
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
        val reminderRequest = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "DailyReminder",
            ExistingPeriodicWorkPolicy.KEEP,
            reminderRequest
        )
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
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
