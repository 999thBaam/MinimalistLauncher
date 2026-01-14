package com.minimalist.launcher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * NotificationIndicatorView: 2-Box Attention Indicator
 * 
 * Two boxes in top-left corner:
 * - IMPORTANT: Glows when urgent, shows badge count
 * - UNIMPORTANT: Shows badge count for stored notifications
 * 
 * Detects clicks on individual boxes.
 */
class NotificationIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    // State
    private var importantCount = 0
    private var unimportantCount = 0
    private var isUrgent = false
    
    // Animation
    private var glowAlpha = 0f
    private var glowAnimator: ValueAnimator? = null
    
    // Paints
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#1E1E1E")  // Dark box
    }
    
    private val importantGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.parseColor("#4CAF50")  // Green glow
    }
    
    private val unimportantGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#666666")  // Gray border
    }
    
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }
    
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AAAAAA")
        textSize = 20f
        textAlign = Paint.Align.CENTER
    }
    
    // Box dimensions
    private val boxWidth = 120f
    private val boxHeight = 60f
    private val boxSpacing = 16f
    private val cornerRadius = 12f
    
    private val importantBox = RectF()
    private val unimportantBox = RectF()
    
    // Click listeners
    private var onImportantClickListener: (() -> Unit)? = null
    private var onUnimportantClickListener: (() -> Unit)? = null
    
    init {
        isClickable = true
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        
        // Position boxes at top-left
        val startX = 24f
        val startY = 24f
        
        importantBox.set(
            startX,
            startY,
            startX + boxWidth,
            startY + boxHeight
        )
        
        unimportantBox.set(
            startX + boxWidth + boxSpacing,
            startY,
            startX + boxWidth * 2 + boxSpacing,
            startY + boxHeight
        )
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw IMPORTANT box
        canvas.drawRoundRect(importantBox, cornerRadius, cornerRadius, boxPaint)
        
        // Glow effect for important (pulsing when urgent)
        if (importantCount > 0) {
            if (isUrgent) {
                importantGlowPaint.alpha = (255 * glowAlpha).toInt()
                importantGlowPaint.strokeWidth = 4f + (glowAlpha * 4f)
            } else {
                importantGlowPaint.alpha = 180
                importantGlowPaint.strokeWidth = 3f
            }
            canvas.drawRoundRect(importantBox, cornerRadius, cornerRadius, importantGlowPaint)
        }
        
        // Draw UNIMPORTANT box
        canvas.drawRoundRect(unimportantBox, cornerRadius, cornerRadius, boxPaint)
        canvas.drawRoundRect(unimportantBox, cornerRadius, cornerRadius, unimportantGlowPaint)
        
        // Draw badge counts
        if (importantCount > 0) {
            canvas.drawText(
                importantCount.toString(),
                importantBox.centerX(),
                importantBox.centerY() + 8f,
                textPaint
            )
        } else {
            canvas.drawText("!", importantBox.centerX(), importantBox.centerY() + 8f, labelPaint)
        }
        
        if (unimportantCount > 0) {
            canvas.drawText(
                unimportantCount.toString(),
                unimportantBox.centerX(),
                unimportantBox.centerY() + 8f,
                textPaint
            )
        } else {
            canvas.drawText("○", unimportantBox.centerX(), unimportantBox.centerY() + 8f, labelPaint)
        }
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val x = event.x
            val y = event.y
            
            if (importantBox.contains(x, y)) {
                onImportantClickListener?.invoke()
                performClick()
                return true
            } else if (unimportantBox.contains(x, y)) {
                onUnimportantClickListener?.invoke()
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
    
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
    
    /**
     * Update notification counts and urgency state.
     */
    fun updateState(important: Int, unimportant: Int, urgent: Boolean) {
        val wasUrgent = isUrgent
        
        importantCount = important
        unimportantCount = unimportant
        isUrgent = urgent
        
        // Start glow animation if urgent
        if (urgent && !wasUrgent) {
            startGlowAnimation()
        } else if (!urgent && wasUrgent) {
            stopGlowAnimation()
        }
        
        invalidate()
    }
    
    fun setOnImportantClickListener(listener: () -> Unit) {
        onImportantClickListener = listener
    }
    
    fun setOnUnimportantClickListener(listener: () -> Unit) {
        onUnimportantClickListener = listener
    }
    
    private fun startGlowAnimation() {
        glowAnimator?.cancel()
        glowAnimator = ValueAnimator.ofFloat(0.3f, 1f).apply {
            duration = 800
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                glowAlpha = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }
    
    private fun stopGlowAnimation() {
        glowAnimator?.cancel()
        glowAlpha = 0.8f  // Steady glow for non-urgent
        invalidate()
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        glowAnimator?.cancel()
    }
}
