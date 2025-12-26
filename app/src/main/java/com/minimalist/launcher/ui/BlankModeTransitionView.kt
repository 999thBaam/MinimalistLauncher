package com.minimalist.launcher.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator

/**
 * "Dramatic Blank Mode Transition"
 * 
 * Three-phase animation:
 * 1. THE DRAIN - Colors desaturate (full color → grayscale)
 * 2. THE VOID - Black overlay fades in
 * 3. THE REVEAL - "Enter the Blank Mode" button emerges
 * 
 * Creates a narrative: "Powering down... System Off... Mode Ready"
 */
class BlankModeTransitionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Animation state
    private var saturation = 1f  // 1 = full color, 0 = grayscale
    private var darknessAlpha = 0f  // 0 = transparent, 1 = full black
    private var buttonAlpha = 0f
    private var buttonScale = 0.8f
    
    // Animators
    private var saturationAnimator: ValueAnimator? = null
    private var darknessAnimator: ValueAnimator? = null
    private var buttonAnimator: ValueAnimator? = null
    
    // Paints
    private val darknessPaint = Paint().apply {
        color = Color.BLACK
    }
    
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }
    
    private val buttonTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textAlign = Paint.Align.CENTER
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
    
    private val colorMatrix = ColorMatrix()
    private val barPaint = Paint()
    
    private var buttonRect = RectF()
    private val buttonText = "Enter the Blank Mode"
    
    private var hasStarted = false
    private var onComplete: (() -> Unit)? = null
    
    init {
        buttonTextPaint.textSize = 18 * resources.displayMetrics.density
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val w = width.toFloat()
        val h = height.toFloat()
        
        // LAYER 1: Simulated colorful content (with desaturation applied)
        colorMatrix.setSaturation(saturation)
        barPaint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        
        val barHeight = h / colorBars.size
        colorBars.forEachIndexed { index, color ->
            barPaint.color = color
            canvas.drawRect(
                0f,
                index * barHeight,
                w,
                (index + 1) * barHeight,
                barPaint
            )
        }
        
        // LAYER 2: Darkness overlay
        if (darknessAlpha > 0f) {
            darknessPaint.alpha = (darknessAlpha * 255).toInt()
            canvas.drawRect(0f, 0f, w, h, darknessPaint)
        }
        
        // LAYER 3: The Button (only when darkness is substantial)
        if (buttonAlpha > 0f) {
            val density = resources.displayMetrics.density
            val buttonWidth = 280 * density
            val buttonHeight = 56 * density
            val cornerRadius = 28 * density
            
            val centerX = w / 2
            val centerY = h / 2
            
            // Calculate button rect with scale
            val scaledWidth = buttonWidth * buttonScale
            val scaledHeight = buttonHeight * buttonScale
            
            buttonRect.set(
                centerX - scaledWidth / 2,
                centerY - scaledHeight / 2,
                centerX + scaledWidth / 2,
                centerY + scaledHeight / 2
            )
            
            // Draw button
            buttonPaint.alpha = (buttonAlpha * 255).toInt()
            canvas.drawRoundRect(buttonRect, cornerRadius * buttonScale, cornerRadius * buttonScale, buttonPaint)
            
            // Draw text
            buttonTextPaint.alpha = (buttonAlpha * 255).toInt()
            val textY = centerY + buttonTextPaint.textSize / 3
            canvas.drawText(buttonText, centerX, textY, buttonTextPaint)
        }
    }
    
    /**
     * Start the dramatic transition sequence
     */
    fun startTransition(onComplete: (() -> Unit)? = null) {
        if (hasStarted) return
        hasStarted = true
        this.onComplete = onComplete
        
        // Phase 1: THE DRAIN - Desaturate over 1.5s
        saturationAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 1500
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                saturation = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
        
        // Phase 2: THE VOID - Fade in darkness after 800ms
        postDelayed({
            darknessAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1000
                interpolator = AccelerateInterpolator()
                addUpdateListener { anim ->
                    darknessAlpha = anim.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }, 800)
        
        // Phase 3: THE REVEAL - Button appears after 1800ms
        postDelayed({
            buttonAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 800
                interpolator = DecelerateInterpolator()
                addUpdateListener { anim ->
                    val progress = anim.animatedValue as Float
                    buttonAlpha = progress
                    buttonScale = 0.8f + (0.2f * progress)  // Scale from 0.8 to 1.0
                    invalidate()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        this@BlankModeTransitionView.onComplete?.invoke()
                    }
                })
                start()
            }
        }, 1800)
    }
    
    /**
     * Reset to initial state
     */
    fun reset() {
        hasStarted = false
        saturation = 1f
        darknessAlpha = 0f
        buttonAlpha = 0f
        buttonScale = 0.8f
        
        saturationAnimator?.cancel()
        darknessAnimator?.cancel()
        buttonAnimator?.cancel()
        
        barPaint.colorFilter = null
        invalidate()
    }
    
    /**
     * Check if button was clicked
     */
    fun isButtonClicked(x: Float, y: Float): Boolean {
        return buttonAlpha > 0.5f && buttonRect.contains(x, y)
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        saturationAnimator?.cancel()
        darknessAnimator?.cancel()
        buttonAnimator?.cancel()
    }
}
