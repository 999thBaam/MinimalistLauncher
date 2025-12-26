package com.minimalist.launcher.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator

/**
 * "Maglev Shift" - Pin Demo Animation
 * 
 * Visually demonstrates the pinning mechanic:
 * 1. Three app rows displayed
 * 2. A phantom touch appears on bottom item (long-press simulation)
 * 3. Bottom item detaches, floats up, snaps to top
 * 4. Pin icon appears
 * 
 * This teaches without words - "Show, don't tell."
 */
class PinDemoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Paints
    private val rowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
    }
    
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.LEFT
    }
    
    private val touchRipplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        alpha = 50
        style = Paint.Style.FILL
    }
    
    private val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }
    
    // Animation state (0-1 looping)
    private var animProgress = 0f
    private var animator: ValueAnimator? = null
    
    // Layout dimensions
    private var rowHeight = 0f
    private var rowGap = 0f
    private var rowWidth = 0f
    private var cornerRadius = 0f
    private var startX = 0f
    private var startY = 0f
    
    // App names for demo
    private val apps = listOf("Camera", "Clock", "Notes")
    
    private val rowRects = Array(3) { RectF() }
    
    private var hasStarted = false
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        
        val density = resources.displayMetrics.density
        rowHeight = 56 * density
        rowGap = 12 * density
        rowWidth = w * 0.85f
        cornerRadius = 14 * density
        startX = (w - rowWidth) / 2f
        startY = (h - (rowHeight * 3 + rowGap * 2)) / 2f
        
        textPaint.textSize = 16 * density
        pinPaint.textSize = 18 * density
        borderPaint.strokeWidth = 1.5f * density
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Calculate animation phases
        // 0.0 - 0.2: Idle
        // 0.2 - 0.4: Long press (scale up bottom item)
        // 0.4 - 0.7: Move to top
        // 0.7 - 1.0: Show pin, then reset
        
        val isLongPressing = animProgress in 0.2f..0.65f
        val moveProgress = when {
            animProgress < 0.4f -> 0f
            animProgress > 0.7f -> 1f
            else -> (animProgress - 0.4f) / 0.3f  // 0 to 1 during move phase
        }
        val showPin = animProgress > 0.75f
        val heroScale = if (isLongPressing) 1.03f else 1f
        
        // Calculate positions
        // Original positions: Item 0 at top, Item 1 middle, Item 2 (hero) bottom
        // After move: Item 2 at top, Items 0 and 1 shift down
        
        val row0OriginalY = startY
        val row1OriginalY = startY + rowHeight + rowGap
        val row2OriginalY = startY + (rowHeight + rowGap) * 2
        
        // After reorder: hero goes to top, others shift down
        val row0TargetY = row1OriginalY  // Camera shifts to middle
        val row1TargetY = row2OriginalY  // Clock shifts to bottom
        val row2TargetY = row0OriginalY  // Notes goes to top
        
        // Interpolate positions with easing
        val eased = accelerateDecelerate(moveProgress)
        
        val row0Y = lerp(row0OriginalY, row0TargetY, eased)
        val row1Y = lerp(row1OriginalY, row1TargetY, eased)
        val row2Y = lerp(row2OriginalY, row2TargetY, eased)
        
        // Draw rows
        drawRow(canvas, 0, "Camera", row0Y, isHero = false, scale = 1f, showPin = false, showTouch = false)
        drawRow(canvas, 1, "Clock", row1Y, isHero = false, scale = 1f, showPin = false, showTouch = false)
        drawRow(canvas, 2, "Notes", row2Y, isHero = true, scale = heroScale, showPin = showPin, showTouch = isLongPressing && moveProgress < 0.3f)
    }
    
    private fun drawRow(
        canvas: Canvas,
        index: Int,
        text: String,
        y: Float,
        isHero: Boolean,
        scale: Float,
        showPin: Boolean,
        showTouch: Boolean
    ) {
        val rect = rowRects[index]
        
        // Calculate scaled dimensions
        val scaledWidth = rowWidth * scale
        val scaledHeight = rowHeight * scale
        val scaledX = startX - (scaledWidth - rowWidth) / 2
        val scaledY = y - (scaledHeight - rowHeight) / 2
        
        rect.set(scaledX, scaledY, scaledX + scaledWidth, scaledY + scaledHeight)
        
        // Background
        rowPaint.color = if (isHero) Color.parseColor("#1A1A1A") else Color.parseColor("#0A0A0A")
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, rowPaint)
        
        // Border
        borderPaint.color = if (isHero) Color.WHITE else Color.parseColor("#333333")
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)
        
        // Text
        textPaint.color = if (isHero) Color.WHITE else Color.GRAY
        val textX = rect.left + 20 * resources.displayMetrics.density
        val textY = rect.centerY() + textPaint.textSize / 3
        canvas.drawText(text, textX, textY, textPaint)
        
        // Pin icon (simple "📌" representation)
        if (showPin) {
            val pinX = rect.right - 30 * resources.displayMetrics.density
            val pinY = rect.centerY() + pinPaint.textSize / 3
            pinPaint.alpha = ((animProgress - 0.75f) / 0.1f * 255).toInt().coerceIn(0, 255)
            canvas.drawText("📌", pinX, pinY, pinPaint)
        }
        
        // Touch ripple indicator
        if (showTouch) {
            val rippleRadius = 24 * resources.displayMetrics.density
            val pulseScale = 1f + 0.2f * kotlin.math.sin((animProgress * 10 * Math.PI).toFloat())
            touchRipplePaint.alpha = 40
            canvas.drawCircle(rect.centerX(), rect.centerY(), rippleRadius * pulseScale, touchRipplePaint)
        }
    }
    
    private fun lerp(start: Float, end: Float, fraction: Float): Float {
        return start + (end - start) * fraction
    }
    
    private fun accelerateDecelerate(t: Float): Float {
        return (kotlin.math.cos((t + 1) * Math.PI) / 2.0f + 0.5f).toFloat()
    }
    
    /**
     * Start the looping animation
     */
    fun startAnimation() {
        if (hasStarted) return
        hasStarted = true
        
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3500  // 3.5 second loop
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            
            addUpdateListener { anim ->
                animProgress = anim.animatedValue as Float
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
        animProgress = 0f
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
        val desiredHeight = (220 * resources.displayMetrics.density).toInt()
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }
}
