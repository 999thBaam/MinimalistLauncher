package com.minimalist.launcher.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import com.minimalist.launcher.R
import kotlin.math.sin

/**
 * Custom view that displays a single digit in a split-flap / flipper clock style.
 * Features:
 * - Retro split-flap appearance with top highlight and center divider
 * - Physically accurate 3D flip animation with proper depth
 * - Natural motion with AccelerateDecelerateInterpolator
 * - Non-linear shadow for realism
 * - Side hinge decorations
 */
class FlipperDigitView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var currentDigit: Int = 0
    private var previousDigit: Int = 0
    private var isAnimating = false
    
    // Animation progress: 0 = start, 0.5 = halfway (top at 90°), 1.0 = complete
    private var flipProgress: Float = 0f
    
    // Paints
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.flipper_text)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        strokeWidth = 2f
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hingePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.flipper_hinge)
    }
    private val hingeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    // Dimensions
    private val cornerRadius = 6f * resources.displayMetrics.density
    private val hingeWidth = 6f * resources.displayMetrics.density
    private val hingeHeight = 10f * resources.displayMetrics.density
    
    // Animation - Camera for 3D transforms
    private val camera = Camera()
    private val matrix = Matrix()
    private var animator: ValueAnimator? = null
    
    private var cardRect = RectF()
    private var topRect = RectF()
    private var bottomRect = RectF()

    init {
        backgroundPaint.color = ContextCompat.getColor(context, R.color.flipper_background)
        
        // Read custom attributes if provided
        attrs?.let {
            val a = context.obtainStyledAttributes(it, R.styleable.FlipperDigitView)
            currentDigit = a.getInt(R.styleable.FlipperDigitView_digit, 0)
            val customTextSize = a.getDimension(R.styleable.FlipperDigitView_digitTextSize, 0f)
            if (customTextSize > 0) {
                textPaint.textSize = customTextSize
            }
            a.recycle()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        
        val centerY = h / 2f
        cardRect.set(hingeWidth / 2, 0f, w - hingeWidth / 2, h.toFloat())
        topRect.set(cardRect.left, cardRect.top, cardRect.right, centerY)
        bottomRect.set(cardRect.left, centerY, cardRect.right, cardRect.bottom)
        
        // Set text size based on height
        textPaint.textSize = h * 0.65f
        
        // Create top highlight gradient
        highlightPaint.shader = LinearGradient(
            0f, 0f, 0f, h * 0.5f,
            intArrayOf(
                Color.parseColor("#1AFFFFFF"),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val w = width.toFloat()
        val h = height.toFloat()
        val centerY = h / 2
        
        if (isAnimating) {
            drawFlipAnimation(canvas, w, h, centerY)
        } else {
            drawStaticCard(canvas, w, h, centerY, currentDigit)
        }
        
        // Draw center divider line (always on top)
        canvas.drawLine(cardRect.left, centerY, cardRect.right, centerY, dividerPaint)
        
        // Draw hinges (always on top)
        drawHinge(canvas, -hingeWidth / 4, centerY)
        drawHinge(canvas, w - hingeWidth * 0.75f, centerY)
    }
    
    private fun drawStaticCard(canvas: Canvas, w: Float, h: Float, centerY: Float, digit: Int) {
        // Draw main card background
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, backgroundPaint)
        
        // Draw top half highlight
        canvas.save()
        canvas.clipRect(topRect)
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, highlightPaint)
        canvas.restore()
        
        // Draw digit
        val textY = h / 2 - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(digit.toString(), w / 2, textY, textPaint)
    }
    
    private fun drawFlipAnimation(canvas: Canvas, w: Float, h: Float, centerY: Float) {
        // Reset matrix each frame to prevent drift
        matrix.reset()
        
        // Set camera depth for real 3D perspective
        camera.setLocation(0f, 0f, -12f * resources.displayMetrics.density)
        
        val isFirstHalf = flipProgress < 0.5f
        val phaseProgress = if (isFirstHalf) flipProgress * 2f else (flipProgress - 0.5f) * 2f
        val rotation = phaseProgress * 90f  // Correct 0-90° per phase
        
        if (isFirstHalf) {
            // PHASE 1: Top flaps down (shows previous digit leaving)
            
            // Draw static bottom half (previous digit)
            canvas.save()
            canvas.clipRect(bottomRect)
            drawStaticCard(canvas, w, h, centerY, previousDigit)
            canvas.restore()
            
            // Draw static new digit's bottom underneath (will be revealed)
            canvas.save()
            canvas.clipRect(bottomRect)
            drawStaticCard(canvas, w, h, centerY, currentDigit)
            canvas.restore()
            
            // Draw rotating top half (previous digit)
            canvas.save()
            camera.save()
            camera.rotateX(rotation)
            camera.getMatrix(matrix)
            camera.restore()
            
            // Pivot from center of divider
            matrix.preTranslate(-w / 2f, -centerY)
            matrix.postTranslate(w / 2f, centerY)
            canvas.concat(matrix)
            
            canvas.clipRect(topRect)
            drawStaticCard(canvas, w, h, centerY, previousDigit)
            
            // Non-linear shadow (strongest near 90°)
            val shadowAlpha = (sin(Math.toRadians(rotation.toDouble())) * 120).toInt()
            shadowPaint.color = Color.argb(shadowAlpha, 0, 0, 0)
            canvas.drawRect(topRect, shadowPaint)
            
            canvas.restore()
            
        } else {
            // PHASE 2: Bottom flaps up (shows new digit arriving)
            
            // Draw static top half (new digit)
            canvas.save()
            canvas.clipRect(topRect)
            drawStaticCard(canvas, w, h, centerY, currentDigit)
            canvas.restore()
            
            // Draw rotating bottom half (new digit)
            canvas.save()
            camera.save()
            camera.rotateX(-90f + rotation)  // From -90° to 0°
            camera.getMatrix(matrix)
            camera.restore()
            
            // Pivot from center of divider
            matrix.preTranslate(-w / 2f, -centerY)
            matrix.postTranslate(w / 2f, centerY)
            canvas.concat(matrix)
            
            canvas.clipRect(bottomRect)
            drawStaticCard(canvas, w, h, centerY, currentDigit)
            
            // Shadow fading out
            val shadowAlpha = (sin(Math.toRadians((90f - rotation).toDouble())) * 120).toInt()
            shadowPaint.color = Color.argb(shadowAlpha.coerceIn(0, 120), 0, 0, 0)
            canvas.drawRect(bottomRect, shadowPaint)
            
            canvas.restore()
        }
    }
    
    private fun drawHinge(canvas: Canvas, x: Float, centerY: Float) {
        val hingeRect = RectF(
            x,
            centerY - hingeHeight / 2,
            x + hingeWidth,
            centerY + hingeHeight / 2
        )
        canvas.drawRoundRect(hingeRect, 2f, 2f, hingePaint)
        canvas.drawRoundRect(hingeRect, 2f, 2f, hingeBorderPaint)
    }

    /**
     * Set the digit to display with optional animation.
     */
    fun setDigit(digit: Int, animate: Boolean = true) {
        if (digit == currentDigit) return
        
        previousDigit = currentDigit
        currentDigit = digit
        
        if (animate) {
            startFlipAnimation()
        } else {
            invalidate()
        }
    }
    
    fun getDigit(): Int = currentDigit
    
    private fun startFlipAnimation() {
        animator?.cancel()
        
        isAnimating = true
        flipProgress = 0f
        
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 420  // Slightly longer for premium feel
            interpolator = AccelerateDecelerateInterpolator()  // Natural physics
            
            addUpdateListener { animation ->
                flipProgress = animation.animatedValue as Float
                invalidate()
            }
            
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isAnimating = false
                    // Do NOT reset flipProgress here to avoid visual popping
                    invalidate()
                }
            })
            
            start()
        }
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Default size: 64dp x 96dp
        val defaultWidth = (64 * resources.displayMetrics.density).toInt()
        val defaultHeight = (96 * resources.displayMetrics.density).toInt()
        
        val width = resolveSize(defaultWidth, widthMeasureSpec)
        val height = resolveSize(defaultHeight, heightMeasureSpec)
        
        setMeasuredDimension(width, height)
    }
}
