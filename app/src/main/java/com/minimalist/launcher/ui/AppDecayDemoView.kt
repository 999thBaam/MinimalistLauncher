package com.minimalist.launcher.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * "Ghosting" - App Decay Demo Animation
 * 
 * Visually demonstrates the "Use it or Lose it" rule:
 * 1. Two apps side-by-side: Used vs Neglected
 * 2. Day counter rapidly counts up: Day 1 → Day 30
 * 3. Neglected app fades to near-invisible "ghosted" state
 * 4. Used app stays bright white
 * 
 * Triggers loss aversion - creates strong memory of the feature.
 */
class AppDecayDemoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Paints
    private val dayCounterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        textAlign = Paint.Align.CENTER
        typeface = Typeface.MONOSPACE
    }
    
    private val appNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
    
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        textAlign = Paint.Align.CENTER
    }
    
    private val strikePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    
    // Animation state (0-35 representing days)
    private var dayCounter = 0f
    private var animator: ValueAnimator? = null
    
    // Layout constants
    private var density = 1f
    
    // App names
    private val usedApp = "WhatsApp"
    private val neglectedApp = "Candy Crush"
    
    private var hasStarted = false
    
    init {
        density = resources.displayMetrics.density
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        
        dayCounterPaint.textSize = 14 * density
        appNamePaint.textSize = 18 * density
        labelPaint.textSize = 11 * density
        strikePaint.strokeWidth = 2 * density
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val centerX = width / 2f
        val leftX = width * 0.25f
        val rightX = width * 0.75f
        
        // Calculate neglected app opacity
        val opacity = calculateOpacity(dayCounter)
        val isGhosted = opacity < 0.3f
        
        // --- DAY COUNTER ---
        val dayText = "Days since last open: ${dayCounter.toInt()}"
        val counterY = height * 0.15f
        canvas.drawText(dayText, centerX, counterY, dayCounterPaint)
        
        // --- LEFT APP (Used Daily) ---
        val appY = height * 0.5f
        val labelY = appY + 24 * density
        
        // Subtle pulse effect on "used" app every few days
        val usedScale = if ((dayCounter.toInt() % 5) == 0 && dayCounter > 1) 1.05f else 1f
        
        canvas.save()
        canvas.scale(usedScale, usedScale, leftX, appY - 8 * density)
        appNamePaint.color = Color.WHITE
        appNamePaint.alpha = 255
        canvas.drawText(usedApp, leftX, appY, appNamePaint)
        canvas.restore()
        
        labelPaint.color = Color.parseColor("#4CAF50")  // Green
        labelPaint.alpha = 255
        canvas.drawText("Used Often ✓", leftX, labelY, labelPaint)
        
        // --- RIGHT APP (Neglected) ---
        val alphaInt = (opacity * 255).toInt().coerceIn(25, 255)
        
        appNamePaint.color = Color.WHITE
        appNamePaint.alpha = alphaInt
        canvas.drawText(neglectedApp, rightX, appY, appNamePaint)
        
        // Strikethrough when ghosted
        if (isGhosted) {
            val textWidth = appNamePaint.measureText(neglectedApp)
            strikePaint.alpha = alphaInt
            canvas.drawLine(
                rightX - textWidth / 2 - 4 * density,
                appY - 6 * density,
                rightX + textWidth / 2 + 4 * density,
                appY - 6 * density,
                strikePaint
            )
        }
        
        // Label changes based on state
        val statusText = when {
            dayCounter < 10 -> "Unused"
            dayCounter < 20 -> "Fading..."
            isGhosted -> "👻 GHOSTED"
            else -> "Almost gone"
        }
        
        labelPaint.color = if (isGhosted) Color.parseColor("#FF5252") else Color.GRAY
        labelPaint.alpha = if (isGhosted) 255 else alphaInt
        canvas.drawText(statusText, rightX, labelY, labelPaint)
        
        // --- VISUAL DECAY PARTICLES (when fading) ---
        if (dayCounter > 15 && dayCounter < 35) {
            drawDecayParticles(canvas, rightX, appY, opacity)
        }
    }
    
    private fun drawDecayParticles(canvas: Canvas, x: Float, y: Float, opacity: Float) {
        val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GRAY
            alpha = ((1f - opacity) * 100).toInt().coerceIn(0, 100)
        }
        
        // Draw some "dust" particles floating away
        val particleCount = ((1f - opacity) * 5).toInt()
        for (i in 0 until particleCount) {
            val offsetX = (Math.random() * 60 - 30).toFloat() * density
            val offsetY = (Math.random() * 40 - 20).toFloat() * density + (dayCounter - 15) * density
            val size = (Math.random() * 3 + 1).toFloat() * density
            
            canvas.drawCircle(x + offsetX, y + offsetY, size, particlePaint)
        }
    }
    
    private fun calculateOpacity(days: Float): Float {
        if (days < 10) return 1f  // Grace period
        
        // Decay from day 10 to day 30
        val decayProgress = (days - 10) / 20f
        return (1f - decayProgress).coerceIn(0.1f, 1f)
    }
    
    /**
     * Start the looping animation
     */
    fun startAnimation() {
        if (hasStarted) return
        hasStarted = true
        
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 35f).apply {
            duration = 4500  // 4.5 second loop
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            
            addUpdateListener { anim ->
                dayCounter = anim.animatedValue as Float
                invalidate()
            }
            
            start()
        }
    }
    
    /**
     * Stop and reset the animation
     */
    fun stopAnimation() {
        animator?.cancel()
        hasStarted = false
        dayCounter = 0f
        invalidate()
    }
    
    fun reset() {
        stopAnimation()
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (160 * resources.displayMetrics.density).toInt()
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }
}
