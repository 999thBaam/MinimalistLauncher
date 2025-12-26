package com.minimalist.launcher.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.minimalist.launcher.R

/**
 * "True focus has no color." animation view.
 * 
 * The word "color" cycles through a rainbow gradient, then gets
 * struck through with a white line and turns gray.
 * 
 * This creates a micro-narrative demonstrating the app's philosophy.
 */
class ColorKillerTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Text paints
    private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    
    private val colorWordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
    
    private val strikePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }
    
    // Animation state
    private var rainbowOffset = 0f
    private var strikeProgress = 0f
    private var isKilled = false
    
    // Animators
    private var rainbowAnimator: ValueAnimator? = null
    private var strikeAnimator: ValueAnimator? = null
    
    // Rainbow colors
    private val rainbowColors = intArrayOf(
        Color.parseColor("#FF0000"),  // Red
        Color.parseColor("#FF7F00"),  // Orange
        Color.parseColor("#FFFF00"),  // Yellow
        Color.parseColor("#00FF00"),  // Green
        Color.parseColor("#0000FF"),  // Blue
        Color.parseColor("#4B0082"),  // Indigo
        Color.parseColor("#9400D3"),  // Violet
        Color.parseColor("#FF0000")   // Back to Red (loop)
    )
    
    // Text content
    private val prefixText = "True focus has no "
    private val colorWord = "color."
    
    // Layout measurements
    private var textSize = 0f
    private var prefixWidth = 0f
    private var colorWordWidth = 0f
    private var textBaseline = 0f
    private var colorWordX = 0f
    
    private var hasAnimated = false
    
    init {
        // Start invisible
        alpha = 0f
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        
        // Calculate text size based on view width
        textSize = w * 0.07f  // 7% of width
        textSize = textSize.coerceIn(24f * resources.displayMetrics.density, 
                                      40f * resources.displayMetrics.density)
        
        whitePaint.textSize = textSize
        colorWordPaint.textSize = textSize
        strikePaint.strokeWidth = textSize * 0.15f
        
        // Measure text
        prefixWidth = whitePaint.measureText(prefixText)
        colorWordWidth = colorWordPaint.measureText(colorWord)
        
        // Center horizontally
        val totalWidth = prefixWidth + colorWordWidth
        val startX = (w - totalWidth) / 2f
        colorWordX = startX + prefixWidth
        
        // Center vertically
        val fontMetrics = whitePaint.fontMetrics
        textBaseline = h / 2f - (fontMetrics.ascent + fontMetrics.descent) / 2f
        
        // Create rainbow gradient
        updateRainbowShader()
    }
    
    private fun updateRainbowShader() {
        val shader = LinearGradient(
            colorWordX - rainbowOffset,
            0f,
            colorWordX + colorWordWidth + 200f - rainbowOffset,
            0f,
            rainbowColors,
            null,
            Shader.TileMode.MIRROR
        )
        colorWordPaint.shader = shader
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val totalWidth = prefixWidth + colorWordWidth
        val startX = (width - totalWidth) / 2f
        
        // Draw prefix in white
        canvas.drawText(prefixText, startX, textBaseline, whitePaint)
        
        // Draw "color" word
        if (isKilled && strikeProgress >= 1f) {
            // Final state: gray text
            colorWordPaint.shader = null
            colorWordPaint.color = Color.DKGRAY
        }
        canvas.drawText(colorWord, colorWordX, textBaseline, colorWordPaint)
        
        // Draw strikethrough line
        if (strikeProgress > 0f) {
            val fontMetrics = colorWordPaint.fontMetrics
            val strikeY = textBaseline + (fontMetrics.ascent + fontMetrics.descent) / 2f
            val strikeEndX = colorWordX + (colorWordWidth * strikeProgress)
            
            canvas.drawLine(
                colorWordX - 4f,
                strikeY,
                strikeEndX + 4f,
                strikeY,
                strikePaint
            )
        }
    }
    
    /**
     * Start the color killer animation sequence.
     */
    fun playAnimation() {
        if (hasAnimated) return
        hasAnimated = true
        
        // Fade in
        animate().alpha(1f).setDuration(300).start()
        
        // Start rainbow cycling
        startRainbowAnimation()
        
        // Kill the color after 1.2 seconds
        postDelayed({
            killTheColor()
        }, 1200)
    }
    
    private fun startRainbowAnimation() {
        rainbowAnimator?.cancel()
        
        rainbowAnimator = ValueAnimator.ofFloat(0f, 400f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            
            addUpdateListener { animator ->
                rainbowOffset = animator.animatedValue as Float
                updateRainbowShader()
                invalidate()
            }
            
            start()
        }
    }
    
    private fun killTheColor() {
        isKilled = true
        
        // Stop rainbow animation
        rainbowAnimator?.cancel()
        
        // Animate the strikethrough
        strikeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 500
            interpolator = AccelerateDecelerateInterpolator()
            
            addUpdateListener { animator ->
                strikeProgress = animator.animatedValue as Float
                invalidate()
            }
            
            start()
        }
        
        // Haptic feedback at the "kill" moment
        postDelayed({
            triggerKillFeedback()
        }, 400)
        
        // Loop: Wait 2 seconds in "dead" state, then restart
        postDelayed({
            restartCycle()
        }, 2500)
    }
    
    private fun restartCycle() {
        // Reset state
        isKilled = false
        strikeProgress = 0f
        
        // Restore rainbow shader
        colorWordPaint.shader = null
        updateRainbowShader()
        invalidate()
        
        // Start rainbow cycling again
        startRainbowAnimation()
        
        // Kill again after 1.5 seconds
        postDelayed({
            killTheColor()
        }, 1500)
    }
    
    private fun triggerKillFeedback() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) 
                    as? android.os.VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    android.os.VibrationEffect.createOneShot(30, android.os.VibrationEffect.EFFECT_TICK)
                )
            }
        } catch (e: Exception) {
            // Haptics not available
        }
    }
    
    /**
     * Reset to allow re-animation
     */
    fun reset() {
        hasAnimated = false
        isKilled = false
        rainbowOffset = 0f
        strikeProgress = 0f
        alpha = 0f
        rainbowAnimator?.cancel()
        strikeAnimator?.cancel()
        
        // Restore rainbow shader
        colorWordPaint.color = Color.WHITE
        updateRainbowShader()
        invalidate()
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        rainbowAnimator?.cancel()
        strikeAnimator?.cancel()
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (80 * resources.displayMetrics.density).toInt()
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }
}
