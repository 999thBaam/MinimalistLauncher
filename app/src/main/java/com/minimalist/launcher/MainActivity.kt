package com.minimalist.launcher

import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.minimalist.launcher.data.AppItem
import com.minimalist.launcher.data.AppRepository
import com.minimalist.launcher.databinding.ActivityMainBinding
import com.minimalist.launcher.ui.AppListAdapter
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
    
    private var allApps: List<AppItem> = emptyList()
    
    private val prefs by lazy { 
        getSharedPreferences("minimalist_prefs", Context.MODE_PRIVATE) 
    }

    // Activity result launcher for setting default home
    private val setDefaultLauncherResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        // Mark as prompted regardless of result
        prefs.edit().putBoolean("has_prompted_default", true).apply()
    }

    // BroadcastReceiver for package install/uninstall events
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Refresh app list when packages change
            loadApps()
        }
    }

    // Receiver for time updates (to keep header current)
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
        
        // Check if we should prompt user to set as default launcher
        checkAndPromptDefaultLauncher()
    }
    
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

    override fun onResume() {
        super.onResume()
        updateHeader()
        registerReceivers()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceivers()
    }

    private fun setupRecyclerView() {
        adapter = AppListAdapter { app ->
            launchApp(app)
        }
        
        binding.appRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
            // Optimize for fixed item heights
            setHasFixedSize(true)
        }
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

    private fun registerReceivers() {
        // Package changes (install/uninstall)
        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, packageFilter)

        // Time changes
        val timeFilter = IntentFilter(Intent.ACTION_TIME_TICK)
        registerReceiver(timeReceiver, timeFilter)
    }

    private fun unregisterReceivers() {
        try {
            unregisterReceiver(packageReceiver)
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
