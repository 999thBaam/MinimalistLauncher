package com.minimalist.launcher.ui.notifications

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.minimalist.launcher.databinding.ActivityNotificationCenterBinding
import com.minimalist.launcher.service.MinimalistNotificationListener

class NotificationCenterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationCenterBinding
    private lateinit var adapter: NotificationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationCenterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupObservers()
        setupListeners()
        
        // Hide status bar for full immersion
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
    }

    private fun setupRecyclerView() {
        adapter = NotificationAdapter()
        binding.notificationConfigRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.notificationConfigRecyclerView.adapter = adapter
        
        // Attach Swipe Semantics
        val touchHelper = androidx.recyclerview.widget.ItemTouchHelper(NotificationSwipeCallback(adapter))
        touchHelper.attachToRecyclerView(binding.notificationConfigRecyclerView)
    }

    private fun setupObservers() {
        // Observe the LiveData (Models) from our bound service
        MinimalistNotificationListener.activeModels.observe(this) { notifications ->
            val list = notifications ?: emptyList()
            adapter.submitList(list)
            
            // Toggle Empty State
            if (list.isEmpty()) {
                binding.emptyStateText.visibility = View.VISIBLE
                binding.notificationConfigRecyclerView.visibility = View.GONE
            } else {
                binding.emptyStateText.visibility = View.GONE
                binding.notificationConfigRecyclerView.visibility = View.VISIBLE
            }
        }
    }
    
    private fun setupListeners() {
        binding.closeButton.setOnClickListener {
            finish()
            overridePendingTransition(0, android.R.anim.fade_out)
        }
        
        binding.clearViewButton.setOnClickListener {
            MinimalistNotificationListener.clearAllNotifications()
            finish()
            overridePendingTransition(0, android.R.anim.fade_out)
        }
    }
    
    // Gesture Detection for Swipe Up to Exit
    private val gestureDetector by lazy {
        android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val diffY = e2.y - e1.y
                val diffX = e2.x - e1.x
                
                // Detect Swipe Up (Vertical > Horizontal, and upward motion)
                if (Math.abs(diffY) > Math.abs(diffX) && diffY < -100 && Math.abs(velocityY) > 100) {
                    // Only exit if we are at the bottom of the list (or list is short)
                    if (!binding.notificationConfigRecyclerView.canScrollVertically(1)) {
                        finish()
                        overridePendingTransition(0, android.R.anim.fade_out)
                        return true
                    }
                }
                return false
            }
        })
    }
    
    override fun dispatchTouchEvent(ev: android.view.MotionEvent?): Boolean {
        ev?.let { gestureDetector.onTouchEvent(it) }
        return super.dispatchTouchEvent(ev)
    }

    override fun onPause() {
        super.onPause()
        if (isFinishing) {
            overridePendingTransition(0, android.R.anim.fade_out)
        }
    }
}
