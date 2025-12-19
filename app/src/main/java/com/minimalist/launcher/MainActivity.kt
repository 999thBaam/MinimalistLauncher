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
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Minimalist Focus Launcher - Main Activity
 * 
 * "Boring by Design" - Grayscale, text-only app drawer to reduce
 * dopamine-driven impulsive app usage.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appRepository: AppRepository
    private lateinit var adapter: AppListAdapter
    
    // ... existing properties ...
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
        
        // Check onboarding status
        if (!prefs.getBoolean("is_onboarding_complete", false)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        
        // Initial setup sequence
        registerPackageReceiver()
        
        // Settings button click -> Show Menu
        binding.settingsButton.setOnClickListener { view ->
            showSettingsMenu(view)
        }
    }
    
    private fun showSettingsMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add("System Home Settings")
        popup.menu.add("Set Black Lockscreen")
        
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "System Home Settings" -> {
                    Toast.makeText(this, "Opening Settings...", Toast.LENGTH_SHORT).show()
                    try {
                        startActivity(Intent(android.provider.Settings.ACTION_HOME_SETTINGS))
                    } catch (e: Exception) {
                        startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                    }
                    true
                }
                "Set Black Lockscreen" -> {
                    setBlackLockscreen()
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
            // Create a 1x1 black bitmap
            val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.BLACK)
            
            // Set as Lockscreen
            wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
            Toast.makeText(this, "Lockscreen set to Black", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(this, "Permission denied. Grant Wallpaper permission.", Toast.LENGTH_LONG).show()
        } catch (e: IOException) {
            Toast.makeText(this, "Failed to set wallpaper", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
             Toast.makeText(this, "Error setting lockscreen", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Checks for required permissions in sequence.
     * 1. Default Launcher (Checked in onCreate/onResume via helper)
     * 2. Usage Stats (Decluttering feature)
     */
    private fun checkPermissions() {
        // Usage Access Check
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
    
    // ... prompt and open launcher picker methods similar to before ...
    // Keeping compact for replacement block matching if needed, 
    // but the diff below targets specific blocks or we can replace the full body parts.
    // I will target setupRecyclerView and Settings click specifically to minimize large overwrites if possible
    // but the imports are at top. I basically need to rewrite imports + onCreate + setupRecyclerView.
    
    // ... [EXISTING METHODS: checkAndPromptDefaultLauncher, openLauncherPicker, isDefaultLauncher] ...

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
                        if (app.isPinned) {
                            appRepository.unpinApp(app.packageName)
                        } else {
                            appRepository.pinApp(app.packageName)
                        }
                        loadApps() // Reload to update sort
                    }
                    1 -> uninstallApp(app)
                }
            }
            .show()
    }

    // ... [Rest of file: setupSearch, loadApps, filterApps, launchApp, uninstallApp, updateHeader, receivers, onBackPressed] ...
    
    /**
     * Checks if this app is the default launcher.
     * If not, and we haven't prompted before, show a dialog and open the picker.
     */
    private fun checkAndPromptDefaultLauncher() {
        if (isDefaultLauncher()) return
        
        val hasPrompted = prefs.getBoolean("has_prompted_default", false)
        if (hasPrompted) return
        
        // Show explanation dialog, then open launcher picker
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
    
    /**
     * Opens the system launcher picker directly.
     */
    private fun openLauncherPicker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+: Use RoleManager for cleaner UX
            val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
            if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                setDefaultLauncherResult.launch(intent)
                return
            }
        }
        
        // Fallback for older Android: Trigger home intent to show picker
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        prefs.edit().putBoolean("has_prompted_default", true).apply()
    }
    
    /**
     * Checks if this app is currently the default launcher.
     */
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
        
        // Re-check permission status to refresh list if user just granted it
        if (appRepository.hasUsageStatsPermission()) {
            loadApps()
        } else {
            // Prompt if we haven't checked recently? 
            // For now, let's keep it simple and check on every resume if not granted, or just once?
            // Existing flow checks in onCreate. Let's add a check here too but maybe less intrusive?
            // Actually, let's just run the check directly. It has its own dialog.
            // But we don't want to spam. The dialog shows every time? 
            // Let's rely on onCreate for the dialog, but use onResume to refresh data.
            // Wait, if user goes to settings and comes back, onCreate isn't called if activity wasn't destroyed.
            // So we SHOULD check in onResume, but maybe protect with a flag or just Rely on the user doing it.
            // "Make sure to take all permission together" is the instruction.
            // So let's check here.
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
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = android.net.Uri.parse("package:${app.packageName}")
        }
        startActivity(intent)
    }

    private fun updateHeader() {
        // Update time (24-hour format for minimalist aesthetic)
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        binding.timeText.text = timeFormat.format(Date())

        // Update date
        val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
        binding.dateText.text = dateFormat.format(Date())

        // Update battery percentage (text only, no icon)
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        binding.batteryText.text = "$batteryLevel%"
    }

    private fun registerPackageReceiver() {
        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
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
}
