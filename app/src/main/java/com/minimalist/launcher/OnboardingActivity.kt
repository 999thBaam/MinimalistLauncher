package com.minimalist.launcher

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
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
        progressBar = findViewById(R.id.onboardingProgressBar)

        setupViewPager()
        setupNavigation()
    }


    private fun setupViewPager() {
        val slides = listOf(
            // Color Killer slide (special type - iconRes = 0)
            OnboardingSlide(
                0,  // Special: COLOR_KILLER type
                "True focus has no color.",
                "Just text.\nJust intention.",
                null
            ),
            OnboardingSlide(
                R.drawable.ic_onboarding_home,
                "Make it home",
                "This only works as your home screen.\n\nOne tap away from focus.",
                "Set as Default"
            ),
            // Decay Demo - Digital decluttering feature (now position 2)
            OnboardingSlide(
                -2,  // Special: DECAY_DEMO type
                "Digital decluttering",
                "• White means active\n• Grey means unused\n• Your screen stays calm",
                "Grant Access"
            ),
            // Finale slide (now position 3)
            OnboardingSlide(
                -3,  // Special: FINALE type
                "Enter the Blank Mode",
                "",
                null
            )
        )

        adapter = OnboardingAdapter(slides, this::onSlideAction)
        viewPager.adapter = adapter
        
        // Disable horizontal swipe - navigation only via buttons
        viewPager.isUserInputEnabled = false
        
        // Subtle fade transformer
        viewPager.setPageTransformer { page, position ->
            page.alpha = 1 - 0.3f * kotlin.math.abs(position)
            page.scaleY = 1 - 0.03f * kotlin.math.abs(position)
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            private var lastAnimatedPosition = -1
            
            override fun onPageSelected(position: Int) {
                updateNavigation(position, slides.size)
                prefs.edit().putInt("onboarding_progress", position).apply()
                
                // Animate the slide when it becomes visible
                // Add delay to let ViewPager settle after transition
                if (position != lastAnimatedPosition) {
                    lastAnimatedPosition = position
                    viewPager.postDelayed({ 
                        animateCurrentSlide(position) 
                    }, 150)  // Wait for page transition to complete
                }
            }
        })
        
        // Restore progress
        val savedPosition = prefs.getInt("onboarding_progress", 0)
        if (savedPosition > 0 && savedPosition < slides.size) {
            viewPager.setCurrentItem(savedPosition, false)
            viewPager.postDelayed({ 
                updateNavigation(savedPosition, slides.size)
                animateCurrentSlide(savedPosition)
            }, 200)
        } else {
            // Initial state - animate first slide after a short delay
            updateNavigation(0, slides.size)
            viewPager.postDelayed({ animateCurrentSlide(0) }, 400)
        }
    }
    
    private fun animateCurrentSlide(position: Int) {
        val recyclerView = viewPager.getChildAt(0) as? RecyclerView ?: return
        val viewHolder = recyclerView.findViewHolderForAdapterPosition(position)
        
        if (viewHolder == null) {
            // ViewHolder not ready, retry after a short delay
            viewPager.postDelayed({ animateCurrentSlide(position) }, 100)
            return
        }
        
        when (position) {
            0 -> {
                // Color Killer slide: Rainbow strikethrough animation
                val colorKillerText = viewHolder.itemView.findViewById<com.minimalist.launcher.ui.ColorKillerTextView>(R.id.colorKillerText)
                val explanationText = viewHolder.itemView.findViewById<TextView>(R.id.explanationText)
                val subtitle = viewHolder.itemView.findViewById<TextView>(R.id.subtitleText)
                
                colorKillerText?.playAnimation()
                
                // Explanation fades in after the "kill" (delayed)
                explanationText?.let { e ->
                    e.alpha = 0f
                    e.translationY = 20f
                    e.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(600)
                        .setStartDelay(1800)  // After rainbow is killed
                        .setInterpolator(androidx.interpolator.view.animation.FastOutSlowInInterpolator())
                        .start()
                }
                
                // Subtitle fades in after explanation
                subtitle?.let { s ->
                    s.alpha = 0f
                    s.translationY = 20f
                    s.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(600)
                        .setStartDelay(2600)  // After explanation appears
                        .setInterpolator(androidx.interpolator.view.animation.FastOutSlowInInterpolator())
                        .start()
                }
            }
            2 -> {
                // Decay Demo slide: Ghosting animation (now position 2)
                val decayDemoView = viewHolder.itemView.findViewById<com.minimalist.launcher.ui.AppDecayDemoView>(R.id.decayDemoView)
                val featureLabel = viewHolder.itemView.findViewById<TextView>(R.id.featureLabel)
                val title = viewHolder.itemView.findViewById<TextView>(R.id.slideTitle)
                val desc = viewHolder.itemView.findViewById<TextView>(R.id.slideDescription)
                
                // Start the looping decay demo
                decayDemoView?.startAnimation()
                
                // Feature label fades in first
                featureLabel?.let { f ->
                    f.alpha = 0f
                    f.animate()
                        .alpha(1f)
                        .setDuration(300)
                        .setStartDelay(0)
                        .setInterpolator(androidx.interpolator.view.animation.FastOutSlowInInterpolator())
                        .start()
                }
                
                // Title fades in after feature label
                title?.let { t ->
                    t.alpha = 0f
                    t.translationY = 30f
                    t.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(400)
                        .setStartDelay(200)
                        .setInterpolator(androidx.interpolator.view.animation.FastOutSlowInInterpolator())
                        .start()
                }
                
                // Tagline fades in after a full decay cycle
                desc?.let { d ->
                    d.alpha = 0f
                    d.translationY = 30f
                    d.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(400)
                        .setStartDelay(2000)  // After first ghost cycle
                        .setInterpolator(androidx.interpolator.view.animation.FastOutSlowInInterpolator())
                        .start()
                }
            }
            3 -> {
                // FINALE slide: Dramatic 3-phase transition (now position 3)
                val transitionView = viewHolder.itemView.findViewById<com.minimalist.launcher.ui.BlankModeTransitionView>(R.id.blankModeTransition)
                
                // Start the dramatic color drain animation
                transitionView?.startTransition()
            }
            else -> {
                // Standard slides: Staggered entrance animation
                val icon = viewHolder.itemView.findViewById<ImageView>(R.id.slideIcon)
                val title = viewHolder.itemView.findViewById<TextView>(R.id.slideTitle)
                val desc = viewHolder.itemView.findViewById<TextView>(R.id.slideDescription)
                
                // Reset states before animating
                icon?.alpha = 0f
                icon?.translationY = 30f
                title?.alpha = 0f
                title?.translationY = 30f
                desc?.alpha = 0f
                desc?.translationY = 30f
                
                // Staggered animation with smooth timing
                icon?.animate()
                    ?.alpha(1f)
                    ?.translationY(0f)
                    ?.setDuration(400)
                    ?.setStartDelay(0)
                    ?.setInterpolator(androidx.interpolator.view.animation.FastOutSlowInInterpolator())
                    ?.start()
                
                title?.animate()
                    ?.alpha(1f)
                    ?.translationY(0f)
                    ?.setDuration(400)
                    ?.setStartDelay(100)
                    ?.setInterpolator(androidx.interpolator.view.animation.FastOutSlowInInterpolator())
                    ?.start()
                
                desc?.animate()
                    ?.alpha(1f)
                    ?.translationY(0f)
                    ?.setDuration(400)
                    ?.setStartDelay(200)
                    ?.setInterpolator(androidx.interpolator.view.animation.FastOutSlowInInterpolator())
                    ?.start()
            }
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
        } else {
            // Normal Navigation
            if (position == total - 1) {
                // FINALE slide: Hide button - only the center button is used
                nextButton.visibility = View.GONE
            } else {
                nextButton.text = "Continue"
                nextButton.visibility = View.VISIBLE
            }
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
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        
        private val TYPE_COLOR_KILLER = 0
        private val TYPE_DECAY_DEMO = 2
        private val TYPE_FINALE = 3
        private val TYPE_STANDARD = 4
        
        fun getItem(position: Int) = slides[position]
        
        override fun getItemViewType(position: Int): Int {
            val slide = slides[position]
            return when (slide.iconRes) {
                0 -> TYPE_COLOR_KILLER   // iconRes = 0
                -2 -> TYPE_DECAY_DEMO    // iconRes = -2
                -3 -> TYPE_FINALE        // iconRes = -3
                else -> TYPE_STANDARD
            }
        }

        // Color Killer ViewHolder
        inner class ColorKillerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val colorKillerText: com.minimalist.launcher.ui.ColorKillerTextView = 
                view.findViewById(R.id.colorKillerText)
            val explanationText: TextView = view.findViewById(R.id.explanationText)
            val subtitle: TextView = view.findViewById(R.id.subtitleText)
            
            fun bind(slide: OnboardingSlide) {
                // Build styled explanation text:
                // "Removing color dramatically increases focus."
                // "dramatically" is white + semibold, rest is soft gray (MD3 dark mode)
                val builder = SpannableStringBuilder()
                val softGray = Color.parseColor("#B0B0B0")  // MD3 secondary text for dark mode
                val whiteColor = Color.WHITE
                
                val part1 = "Removing color "
                val highlight = "dramatically"
                val part2 = " increases\nfocus."  // Line break for balanced visual
                
                builder.append(part1)
                builder.setSpan(ForegroundColorSpan(softGray), 0, part1.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                
                val highlightStart = builder.length
                builder.append(highlight)
                builder.setSpan(ForegroundColorSpan(whiteColor), highlightStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(StyleSpan(Typeface.BOLD), highlightStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                
                builder.append(part2)
                builder.setSpan(ForegroundColorSpan(softGray), builder.length - part2.length, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                
                explanationText.text = builder
                explanationText.alpha = 0f
                
                subtitle.text = slide.description
                subtitle.alpha = 0f
                colorKillerText.reset()
            }
        }
        

        
        // Decay Demo ViewHolder
        inner class DecayDemoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val decayDemoView: com.minimalist.launcher.ui.AppDecayDemoView = 
                view.findViewById(R.id.decayDemoView)
            val title: TextView = view.findViewById(R.id.slideTitle)
            val desc: TextView = view.findViewById(R.id.slideDescription)
            
            fun bind(slide: OnboardingSlide) {
                title.text = slide.title
                desc.text = slide.description
                
                // Reset - animation triggered by onPageSelected
                title.alpha = 0f
                desc.alpha = 0f
                decayDemoView.reset()
            }
        }
        
        // Finale ViewHolder - Dramatic Blank Mode Transition
        inner class FinaleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val transitionView: com.minimalist.launcher.ui.BlankModeTransitionView = 
                view.findViewById(R.id.blankModeTransition)
            
            fun bind(slide: OnboardingSlide) {
                transitionView.reset()
                
                // Set up touch listener for button click
                transitionView.setOnClickListener { v ->
                    val event = android.view.MotionEvent.obtain(
                        0, 0, android.view.MotionEvent.ACTION_DOWN, 
                        v.width / 2f, v.height / 2f, 0
                    )
                    if (transitionView.isButtonClicked(v.width / 2f, v.height / 2f)) {
                        completeOnboarding()
                    }
                    event.recycle()
                }
                
                // Better touch handling
                transitionView.setOnTouchListener { v, event ->
                    if (event.action == android.view.MotionEvent.ACTION_UP) {
                        if (transitionView.isButtonClicked(event.x, event.y)) {
                            completeOnboarding()
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                }
            }
        }

        // Standard slide ViewHolder
        inner class StandardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.slideIcon)
            val title: TextView = view.findViewById(R.id.slideTitle)
            val desc: TextView = view.findViewById(R.id.slideDescription)

            fun bind(slide: OnboardingSlide) {
                icon.setImageResource(slide.iconRes)
                title.text = slide.title
                desc.text = slide.description
                
                // Reset to invisible - animation triggered by onPageSelected
                icon.alpha = 0f
                title.alpha = 0f
                desc.alpha = 0f
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                TYPE_COLOR_KILLER -> {
                    val view = LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_onboarding_slide_color_killer, parent, false)
                    ColorKillerViewHolder(view)
                }

                TYPE_DECAY_DEMO -> {
                    val view = LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_onboarding_slide_decay_demo, parent, false)
                    DecayDemoViewHolder(view)
                }
                TYPE_FINALE -> {
                    val view = LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_onboarding_slide_finale, parent, false)
                    FinaleViewHolder(view)
                }
                else -> {
                    val view = LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_onboarding_slide, parent, false)
                    StandardViewHolder(view)
                }
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (holder) {
                is ColorKillerViewHolder -> holder.bind(slides[position])
                is DecayDemoViewHolder -> holder.bind(slides[position])
                is FinaleViewHolder -> holder.bind(slides[position])
                is StandardViewHolder -> holder.bind(slides[position])
            }
        }

        override fun getItemCount() = slides.size
    }
}

