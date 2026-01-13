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
        // CHANGED: Use Roboto Light for minimalist look
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }
    // Divider paint removed/unused
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.TRANSPARENT 
        strokeWidth = 0f
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    // Hinge paints removed/unused
    private val hingePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.TRANSPARENT
    }
    private val hingeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.TRANSPARENT
        style = Paint.Style.STROKE
        strokeWidth = 0f
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    // ... (Dimensions remain for layout logic) ...
    private val cornerRadius = 6f * resources.displayMetrics.density
    private val hingeWidth = 6f * resources.displayMetrics.density
    private val hingeHeight = 10f * resources.displayMetrics.density
    
    // ... (Animation props remain) ...
    private val camera = Camera()
    private val matrix = Matrix()
    private var animator: ValueAnimator? = null
    
    private var cardRect = RectF()
    private var topRect = RectF()
    private var bottomRect = RectF()

    init {
        // Background color not used anymore (transparent)
        backgroundPaint.color = Color.TRANSPARENT
        
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
        
        // Set text size based on height - increased slightly for better proportions
        textPaint.textSize = h * 0.7f
        
        // Highlight shader not needed
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
        
        // REMOVED: Divider line
        // REMOVED: Hinges
    }
    
    private fun drawStaticCard(canvas: Canvas, w: Float, h: Float, centerY: Float, digit: Int) {
        // REMOVED: Background card drawing
        // REMOVED: Top half highlight drawing
        
        // Draw digit only
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
            // PHASE 1: Top flaps down
            
            // Draw static bottom half (previous digit)
            canvas.save()
            canvas.clipRect(bottomRect)
            drawStaticCard(canvas, w, h, centerY, previousDigit)
            canvas.restore()
            
            // Draw static new digit's bottom underneath
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
            
            // Pivot from center
            matrix.preTranslate(-w / 2f, -centerY)
            matrix.postTranslate(w / 2f, centerY)
            canvas.concat(matrix)
            
            canvas.clipRect(topRect)
            drawStaticCard(canvas, w, h, centerY, previousDigit)
            
            // REMOVED: Shadow rect (would look like a box on floating text)
            
            canvas.restore()
            
        } else {
            // PHASE 2: Bottom flaps up
            
            // Draw static top half (new digit)
            canvas.save()
            canvas.clipRect(topRect)
            drawStaticCard(canvas, w, h, centerY, currentDigit)
            canvas.restore()
            
            // Draw rotating bottom half
            canvas.save()
            camera.save()
            camera.rotateX(-90f + rotation)
            camera.getMatrix(matrix)
            camera.restore()
            
            // Pivot from center
            matrix.preTranslate(-w / 2f, -centerY)
            matrix.postTranslate(w / 2f, centerY)
            canvas.concat(matrix)
            
            canvas.clipRect(bottomRect)
            drawStaticCard(canvas, w, h, centerY, currentDigit)
            
            // REMOVED: Shadow rect
            
            canvas.restore()
        }
    }
    
    // Empty stub for hinge
    private fun drawHinge(canvas: Canvas, x: Float, centerY: Float) {
        // No-op
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
