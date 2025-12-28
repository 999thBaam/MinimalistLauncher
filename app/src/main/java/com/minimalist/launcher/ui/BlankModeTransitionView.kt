package com.minimalist.launcher.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator

/**
 * "Premium Blank Mode Transition"
 * 
 * A seductive, intentional finale that removes anxiety and creates trust.
 * 
 * Three-phase animation with intentional silence:
 * 1. THE DRAIN - Non-linear desaturation (top bars first, organic curve)
 * 2. THE VOID - Near-black with radial vignette (perceptual comfort)
 * 3. THE SILENCE - Pause of 400ms (confidence, not rushing)
 * 4. THE REVEAL - Calm emergence with gravity (no pop, just presence)
 * 
 * "You are safe to let go."
 */
class BlankModeTransitionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // === DESIGN CONSTANTS (Premium Palette) ===
    private val VOID_COLOR = Color.parseColor("#0E0F12")  // Cold near-black, not harsh #000
    private val VOID_CENTER = Color.parseColor("#141519")  // Slightly lighter center
    private val BUTTON_COLOR = Color.parseColor("#F2F2F2")  // Soft off-white, not #FFF
    private val BUTTON_PRESSED_COLOR = Color.parseColor("#D8D8D8")  // Darkened on tap
    private val TEXT_COLOR = Color.parseColor("#111111")  // Near-black text
    private val NARRATIVE_COLOR = Color.parseColor("#808080")  // 50% gray for narrative

    // === Animation State ===
    private var barSaturations = FloatArray(7) { 1f }  // Per-bar saturation for staggered drain
    private var darknessAlpha = 0f
    private var buttonAlpha = 0f
    private var buttonScale = 0.96f  // Start subtle, not dramatic
    private var buttonTranslateY = 4f  // Slight upward drift in dp
    private var narrativeAlpha = 0f
    
    // Tap feedback state
    private var isPressed = false
    private var isTapAnimating = false
    
    // === Animators ===
    private val barAnimators = mutableListOf<ValueAnimator>()
    private var darknessAnimator: ValueAnimator? = null
    private var buttonAnimator: ValueAnimator? = null
    private var narrativeAnimator: ValueAnimator? = null
    private var tapFeedbackAnimator: ValueAnimator? = null
    private var exitAnimator: ValueAnimator? = null
    
    // === Paints ===
    private val darknessPaint = Paint()
    private val vignettePaint = Paint()
    
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = BUTTON_COLOR
    }
    
    private val buttonTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TEXT_COLOR
        textAlign = Paint.Align.CENTER
        // Increased letter spacing for premium feel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            letterSpacing = 0.02f
        }
    }
    
    private val narrativeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = NARRATIVE_COLOR
        textAlign = Paint.Align.CENTER
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            letterSpacing = 0.04f
        }
    }
    
    // Content simulation - colored bars to show desaturation effect
    private val colorBars = listOf(
        Color.parseColor("#FF6B6B"),  // Red
        Color.parseColor("#4ECDC4"),  // Teal
        Color.parseColor("#FFE66D"),  // Yellow
        Color.parseColor("#95E1D3"),  // Mint
        Color.parseColor("#F38181"),  // Coral
        Color.parseColor("#AA96DA"),  // Purple
        Color.parseColor("#FCBAD3"),  // Pink
    )
    
    private val colorMatrices = List(colorBars.size) { ColorMatrix() }
    private val barPaints = List(colorBars.size) { Paint() }
    
    private var buttonRect = RectF()
    private val buttonText = "Enter blank mode"  // Sentence case = quieter
    private val narrativeText = "Distractions powered down."
    
    private var hasStarted = false
    private var onComplete: (() -> Unit)? = null
    
    private val density by lazy { resources.displayMetrics.density }
    
    init {
        // Medium weight text, not bold
        buttonTextPaint.textSize = 17 * resources.displayMetrics.density
        narrativeTextPaint.textSize = 13 * resources.displayMetrics.density
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        
        // Create radial vignette gradient (darker edges, lighter center)
        val centerX = w / 2f
        val centerY = h / 2f
        val radius = maxOf(w, h) * 0.8f
        
        vignettePaint.shader = RadialGradient(
            centerX, centerY, radius,
            VOID_CENTER,  // Lighter center
            VOID_COLOR,   // Darker edges
            Shader.TileMode.CLAMP
        )
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val w = width.toFloat()
        val h = height.toFloat()
        
        // LAYER 1: Simulated colorful content (with per-bar desaturation)
        val barHeight = h / colorBars.size
        colorBars.forEachIndexed { index, color ->
            colorMatrices[index].setSaturation(barSaturations[index])
            barPaints[index].colorFilter = ColorMatrixColorFilter(colorMatrices[index])
            barPaints[index].color = color
            canvas.drawRect(
                0f,
                index * barHeight,
                w,
                (index + 1) * barHeight,
                barPaints[index]
            )
        }
        
        // LAYER 2: Void with radial vignette (perceptual comfort)
        if (darknessAlpha > 0f) {
            // First draw solid near-black
            darknessPaint.color = VOID_COLOR
            darknessPaint.alpha = (darknessAlpha * 255).toInt()
            canvas.drawRect(0f, 0f, w, h, darknessPaint)
            
            // Then overlay with radial gradient for vignette effect
            vignettePaint.alpha = (darknessAlpha * 255).toInt()
            canvas.drawRect(0f, 0f, w, h, vignettePaint)
        }
        
        // LAYER 3: Narrative text (above button, faint)
        if (narrativeAlpha > 0f) {
            narrativeTextPaint.alpha = (narrativeAlpha * 128).toInt()  // Max 50% opacity
            val narrativeY = h / 2f - 60 * density  // Above the button
            canvas.drawText(narrativeText, w / 2f, narrativeY, narrativeTextPaint)
        }
        
        // LAYER 4: The Button (calm emergence)
        if (buttonAlpha > 0f) {
            val buttonWidth = 260 * density
            val buttonHeight = 62 * density  // Taller: 62dp instead of 56dp
            val cornerRadius = 18 * density  // Less pill: 18dp instead of 28dp
            
            val centerX = w / 2f
            val centerY = h / 2f
            
            // Apply scale and vertical drift
            val scaledWidth = buttonWidth * buttonScale
            val scaledHeight = buttonHeight * buttonScale
            val translateY = buttonTranslateY * density * (1f - buttonAlpha)  // Drift upward
            
            buttonRect.set(
                centerX - scaledWidth / 2,
                centerY - scaledHeight / 2 + translateY,
                centerX + scaledWidth / 2,
                centerY + scaledHeight / 2 + translateY
            )
            
            // Button color (changes on press)
            buttonPaint.color = if (isPressed) BUTTON_PRESSED_COLOR else BUTTON_COLOR
            buttonPaint.alpha = (buttonAlpha * 255).toInt()
            canvas.drawRoundRect(buttonRect, cornerRadius * buttonScale, cornerRadius * buttonScale, buttonPaint)
            
            // Text
            buttonTextPaint.alpha = (buttonAlpha * 255).toInt()
            val textY = buttonRect.centerY() + buttonTextPaint.textSize / 3
            canvas.drawText(buttonText, centerX, textY, buttonTextPaint)
        }
    }
    
    /**
     * Start the premium transition sequence
     */
    fun startTransition(onComplete: (() -> Unit)? = null) {
        if (hasStarted) return
        hasStarted = true
        this.onComplete = onComplete
        
        // === PHASE 1: THE DRAIN (Non-linear, staggered) ===
        // Top bars drain first, bottom bars lag
        // Saturation curve: slow → fast → slow (FastOutSlowIn)
        colorBars.forEachIndexed { index, _ ->
            val delay = index * 120L  // 120ms stagger per bar
            val animator = ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 1200
                startDelay = delay
                interpolator = FastOutSlowInInterpolator()  // Organic, not linear
                addUpdateListener { anim ->
                    barSaturations[index] = anim.animatedValue as Float
                    invalidate()
                }
            }
            barAnimators.add(animator)
            animator.start()
        }
        
        // === PHASE 2: THE VOID (after drain settles) ===
        // Starts after 600ms, overlaps with drain tail
        postDelayed({
            darknessAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1200
                interpolator = AccelerateInterpolator(0.8f)  // Gentle acceleration
                addUpdateListener { anim ->
                    darknessAlpha = anim.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }, 600)
        
        // === PHASE 3: THE SILENCE (400ms of nothing) ===
        // Screen is dark. Nothing happens. User leans in.
        // This is where trust is built.
        
        // === PHASE 4: THE REVEAL (after silence) ===
        // Narrative text first (sets the meaning)
        postDelayed({
            narrativeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 600
                interpolator = DecelerateInterpolator()
                addUpdateListener { anim ->
                    narrativeAlpha = anim.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }, 2200)  // 600 + 1200 + 400 silence
        
        // Button emerges with gravity (subtle, no pop)
        postDelayed({
            buttonAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 700
                interpolator = DecelerateInterpolator(1.5f)  // Gentle landing
                addUpdateListener { anim ->
                    val progress = anim.animatedValue as Float
                    buttonAlpha = progress
                    buttonScale = 0.96f + (0.04f * progress)  // 0.96 → 1.0 (subtle)
                    invalidate()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        this@BlankModeTransitionView.onComplete?.invoke()
                    }
                })
                start()
            }
        }, 2500)  // Slightly after narrative starts
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (buttonAlpha < 0.8f || isTapAnimating) return super.onTouchEvent(event)
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (buttonRect.contains(event.x, event.y)) {
                    isPressed = true
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isPressed && buttonRect.contains(event.x, event.y)) {
                    performButtonTap()
                    return true
                }
                isPressed = false
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                isPressed = false
                invalidate()
            }
        }
        return super.onTouchEvent(event)
    }
    
    /**
     * Premium tap feedback: darken → haptic → fade to black → navigate
     */
    private fun performButtonTap() {
        if (isTapAnimating) return
        isTapAnimating = true
        
        // 1. Micro haptic (very light)
        triggerLightHaptic()
        
        // 2. Button darkens for 80ms
        postDelayed({
            isPressed = false
            invalidate()
            
            // 3. Fade everything to true black before navigation
            exitAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 300
                interpolator = AccelerateInterpolator()
                addUpdateListener { anim ->
                    val fade = anim.animatedValue as Float
                    buttonAlpha = fade
                    narrativeAlpha = fade
                    invalidate()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        // Seal the ritual - trigger completion
                        (context as? android.app.Activity)?.let { activity ->
                            // Navigate via the activity
                            performClick()
                        }
                    }
                })
                start()
            }
        }, 80)
    }
    
    private fun triggerLightHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(15, VibrationEffect.EFFECT_TICK)
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.EFFECT_TICK))
            }
        } catch (e: Exception) {
            // Haptic not available, continue silently
        }
    }
    
    /**
     * Check if button was clicked (for external handlers)
     */
    fun isButtonClicked(x: Float, y: Float): Boolean {
        return buttonAlpha > 0.8f && buttonRect.contains(x, y) && !isTapAnimating
    }
    
    /**
     * Reset to initial state
     */
    fun reset() {
        hasStarted = false
        isTapAnimating = false
        isPressed = false
        
        barSaturations = FloatArray(7) { 1f }
        darknessAlpha = 0f
        buttonAlpha = 0f
        buttonScale = 0.96f
        buttonTranslateY = 4f
        narrativeAlpha = 0f
        
        barAnimators.forEach { it.cancel() }
        barAnimators.clear()
        darknessAnimator?.cancel()
        buttonAnimator?.cancel()
        narrativeAnimator?.cancel()
        tapFeedbackAnimator?.cancel()
        exitAnimator?.cancel()
        
        barPaints.forEach { it.colorFilter = null }
        invalidate()
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        barAnimators.forEach { it.cancel() }
        darknessAnimator?.cancel()
        buttonAnimator?.cancel()
        narrativeAnimator?.cancel()
        tapFeedbackAnimator?.cancel()
        exitAnimator?.cancel()
    }
}
