package com.minimalist.launcher

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.minimalist.launcher.data.AppRepository
import kotlin.math.abs

class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var nextButton: Button
    private lateinit var skipButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: OnboardingAdapter
    private lateinit var appRepository: AppRepository

    private val prefs by lazy {
        getSharedPreferences("minimalist_prefs", Context.MODE_PRIVATE)
    }

    private val setDefaultLauncherResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Refresh UI on return
        adapter.notifyItemChanged(4) // Refresh permission slide
        // Check if we can proceed now
        updateNavigation(viewPager.currentItem, adapter.itemCount)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        appRepository = AppRepository(this)
        viewPager = findViewById(R.id.onboardingViewPager)
        nextButton = findViewById(R.id.nextButton)
        skipButton = findViewById(R.id.skipButton)
        progressBar = findViewById(R.id.onboardingProgressBar)

        setupViewPager()
        setupNavigation()
    }

    private fun setupViewPager() {
        val slides = listOf(
            OnboardingSlide(
                R.drawable.ic_onboarding_noise,
                "Reduce visual noise",
                "Bright colors trigger dopamine loops. We restore your focus.",
                null
            ),
            OnboardingSlide(
                R.drawable.ic_onboarding_focus,
                "Break the hook",
                "Apps compete for attention. We remove the bait.",
                null
            ),
            OnboardingSlide(
                R.drawable.ic_onboarding_pin,
                "Pin & sort",
                "Your favorites on top. The rest sorted by usage.",
                null
            ),
            OnboardingSlide(
                R.drawable.ic_onboarding_clean,
                "Stay clean",
                "Unused apps fade away automatically.",
                null
            ),
            OnboardingSlide(
                R.drawable.ic_onboarding_home,
                "Make it home",
                "To work effectively, Minimalist needs to be your home screen.",
                "Set as Default"
            ),
            OnboardingSlide(
                R.drawable.ic_onboarding_chart,
                "Enable sorting",
                "Usage access lets us adapt to your habits.",
                "Grant Access"
            )
        )

        adapter = OnboardingAdapter(slides, this::onSlideAction)
        viewPager.adapter = adapter
        
        // Subtle fade transformer
        viewPager.setPageTransformer { page, position ->
            page.alpha = 1 - 0.5f * abs(position)
            page.scaleY = 1 - 0.05f * abs(position)
        }

        viewPager.setPageTransformer { page, position ->
            page.alpha = 1 - 0.5f * abs(position)
            page.scaleY = 1 - 0.05f * abs(position)
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateNavigation(position, slides.size)
                // Persist progress to handle process restarts (e.g. setting default launcher)
                prefs.edit().putInt("onboarding_progress", position).apply()
            }
        })
        
        // Restore progress
        val savedPosition = prefs.getInt("onboarding_progress", 0)
        if (savedPosition > 0 && savedPosition < slides.size) {
            viewPager.setCurrentItem(savedPosition, false)
            // Force update navigation for the restored position immediately
            viewPager.post { updateNavigation(savedPosition, slides.size) }
        } else {
            // Initial state
            updateNavigation(0, slides.size)
        }
    }

    private fun setupNavigation() {
        nextButton.setOnClickListener {
            val current = viewPager.currentItem
            // Check if current slide has a mandatory action that isn't done
            val slide = adapter.getItem(current)
            if (slide.actionLabel != null) {
                val isDone = isActionComplete(slide.actionLabel)
                if (!isDone) {
                    onSlideAction(slide.actionLabel)
                    return@setOnClickListener
                }
            }

            // Otherwise proceed
            if (current < adapter.itemCount - 1) {
                viewPager.currentItem = current + 1
            } else {
                completeOnboarding()
            }
        }

        skipButton.setOnClickListener {
            completeOnboarding()
        }
    }
    
    // Check if the permission action is complete
    private fun isActionComplete(actionLabel: String): Boolean {
        return when (actionLabel) {
            "Set as Default" -> isDefaultLauncher()
            "Grant Access" -> appRepository.hasUsageStatsPermission()
            else -> true
        }
    }

    private fun updateNavigation(position: Int, total: Int) {
        // Progress Bar
        val progress = ((position + 1) * 100) / total
        progressBar.setProgress(progress, true)
        
        // Permission Check Logic
        val slide = adapter.getItem(position)
        val isPermissionSlide = slide.actionLabel != null
        val isActionDone = if (isPermissionSlide) isActionComplete(slide.actionLabel!!) else true
        
        // Button Logic
        if (isPermissionSlide && !isActionDone) {
            // Mandatory Step: Button does the Action
            nextButton.text = slide.actionLabel
            nextButton.visibility = View.VISIBLE
            skipButton.visibility = View.INVISIBLE // Strict mode: No skipping mandatory setup
        } else {
            // Normal Navigation
            if (position == total - 1) {
                nextButton.text = "Enter Minimal Mode"
                skipButton.visibility = View.GONE
            } else {
                nextButton.text = "Continue"
                skipButton.visibility = View.VISIBLE
            }
            nextButton.visibility = View.VISIBLE
        }
    }

    private fun onSlideAction(action: String) {
        when (action) {
            "Set as Default" -> openLauncherPicker()
            "Grant Access" -> {
                try {
                    startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                } catch (e: Exception) {
                    // Fallback
                }
            }
        }
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
    }

    private fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = packageManager.resolveActivity(intent, 0)
        return resolveInfo?.activityInfo?.packageName == packageName
    }

    override fun onResume() {
        super.onResume()
        adapter.notifyDataSetChanged()
        updateNavigation(viewPager.currentItem, adapter.itemCount)
    }

    private fun completeOnboarding() {
        prefs.edit().putBoolean("is_onboarding_complete", true).apply()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    // --- Adapter & Data Classes ---

    data class OnboardingSlide(
        val iconRes: Int,
        val title: String,
        val description: String,
        val actionLabel: String?
    )

    inner class OnboardingAdapter(
        private val slides: List<OnboardingSlide>,
        private val onAction: (String) -> Unit
    ) : RecyclerView.Adapter<OnboardingAdapter.SlideViewHolder>() {
        
        fun getItem(position: Int) = slides[position]

        inner class SlideViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.slideIcon)
            val title: TextView = view.findViewById(R.id.slideTitle)
            val desc: TextView = view.findViewById(R.id.slideDescription)

            fun bind(slide: OnboardingSlide) {
                icon.setImageResource(slide.iconRes)
                title.text = slide.title
                desc.text = slide.description
                
                // No button inside the slide anymore! Control is moved to the bottom bar.
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlideViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_onboarding_slide, parent, false)
            return SlideViewHolder(view)
        }

        override fun onBindViewHolder(holder: SlideViewHolder, position: Int) {
            holder.bind(slides[position])
        }

        override fun getItemCount() = slides.size
    }
}
