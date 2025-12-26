package com.minimalist.launcher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.AttributeSet
import android.view.View
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/**
 * "Magnetic Snap" onboarding icon animation.
 * 
 * 4 squares fly in from the corners and COLLIDE to form a grid icon.
 * Uses SpringAnimation for physically accurate bounce on impact.
 */
class MagneticGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Paint for squares
    private val squarePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    
    // Flash paint for impact effect
    private val flashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    
    // Animation progress for each square (0 = at starting position, 1 = assembled)
    private var topLeftProgress = 0f
    private var topRightProgress = 0f
    private var bottomLeftProgress = 0f
    private var bottomRightProgress = 0f
    
    // Flash effect alpha
    private var flashAlpha = 0f
    
    // Dimensions (will be set in onSizeChanged)
    private var squareSize = 0f
    private var gap = 0f
    private var flyDistance = 0f
    
    // Spring animations
    private var topLeftSpring: SpringAnimation? = null
    private var topRightSpring: SpringAnimation? = null
    private var bottomLeftSpring: SpringAnimation? = null
    private var bottomRightSpring: SpringAnimation? = null
    
    // State
    private var hasAnimated = false
    private var isAssembled = false
    
    private val squareRects = Array(4) { RectF() }
    
    init {
        // Start invisible
        alpha = 0f
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        
        // Calculate dimensions
        val size = minOf(w, h).toFloat()
        squareSize = size * 0.4f  // Each square is 40% of view
        gap = size * 0.08f       // Small gap between squares
        flyDistance = size * 2.5f // How far they start from center
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val centerX = width / 2f
        val centerY = height / 2f
        val halfSquare = squareSize / 2f
        val halfGap = gap / 2f
        val cornerRadius = squareSize * 0.15f
        
        // Calculate final positions (assembled state)
        val leftX = centerX - halfGap - squareSize
        val rightX = centerX + halfGap
        val topY = centerY - halfGap - squareSize
        val bottomY = centerY + halfGap
        
        // Draw each square with its animated offset
        // Top-Left: flies from top-left corner
        val tlOffset = (1f - topLeftProgress) * flyDistance
        squareRects[0].set(
            leftX - tlOffset, topY - tlOffset,
            leftX + squareSize - tlOffset, topY + squareSize - tlOffset
        )
        canvas.drawRoundRect(squareRects[0], cornerRadius, cornerRadius, squarePaint)
        
        // Top-Right: flies from top-right corner
        val trOffset = (1f - topRightProgress) * flyDistance
        squareRects[1].set(
            rightX + trOffset, topY - trOffset,
            rightX + squareSize + trOffset, topY + squareSize - trOffset
        )
        canvas.drawRoundRect(squareRects[1], cornerRadius, cornerRadius, squarePaint)
        
        // Bottom-Left: flies from bottom-left corner
        val blOffset = (1f - bottomLeftProgress) * flyDistance
        squareRects[2].set(
            leftX - blOffset, bottomY + blOffset,
            leftX + squareSize - blOffset, bottomY + squareSize + blOffset
        )
        canvas.drawRoundRect(squareRects[2], cornerRadius, cornerRadius, squarePaint)
        
        // Bottom-Right: flies from bottom-right corner  
        val brOffset = (1f - bottomRightProgress) * flyDistance
        squareRects[3].set(
            rightX + brOffset, bottomY + brOffset,
            rightX + squareSize + brOffset, bottomY + squareSize + brOffset
        )
        canvas.drawRoundRect(squareRects[3], cornerRadius, cornerRadius, squarePaint)
        
        // Draw flash overlay on impact
        if (flashAlpha > 0) {
            flashPaint.color = Color.argb((flashAlpha * 255).toInt(), 255, 255, 255)
            for (rect in squareRects) {
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, flashPaint)
            }
        }
    }
    
    /**
     * Start the magnetic snap animation.
     * Call this when the slide becomes visible.
     */
    fun playMagneticSnap() {
        if (hasAnimated) return
        hasAnimated = true
        
        // Make visible
        animate().alpha(1f).setDuration(50).start()
        
        // Reset positions
        topLeftProgress = 0f
        topRightProgress = 0f
        bottomLeftProgress = 0f
        bottomRightProgress = 0f
        
        // Create spring force with high bounciness
        val springForce = SpringForce(1f).apply {
            dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY  // 0.5 - visible bounce
            stiffness = SpringForce.STIFFNESS_LOW  // 200 - slower, more dramatic
        }
        
        // Stagger the squares slightly for dramatic effect
        postDelayed({ animateSquare(0, springForce) }, 0)    // Top-left first
        postDelayed({ animateSquare(1, springForce) }, 30)   // Top-right
        postDelayed({ animateSquare(2, springForce) }, 60)   // Bottom-left
        postDelayed({ animateSquare(3, springForce) }, 90)   // Bottom-right (last to land)
        
        // Trigger haptic + flash at impact moment (~350ms)
        postDelayed({
            triggerImpactFeedback()
            playFlashEffect()
        }, 350)
    }
    
    private fun animateSquare(index: Int, springForce: SpringForce) {
        val property = object : FloatPropertyCompat<MagneticGridView>("square$index") {
            override fun getValue(view: MagneticGridView): Float {
                return when (index) {
                    0 -> view.topLeftProgress
                    1 -> view.topRightProgress
                    2 -> view.bottomLeftProgress
                    else -> view.bottomRightProgress
                }
            }
            
            override fun setValue(view: MagneticGridView, value: Float) {
                when (index) {
                    0 -> view.topLeftProgress = value
                    1 -> view.topRightProgress = value
                    2 -> view.bottomLeftProgress = value
                    else -> view.bottomRightProgress = value
                }
                view.invalidate()
            }
        }
        
        SpringAnimation(this, property).apply {
            spring = springForce
            setStartValue(0f)
            start()
        }.also {
            when (index) {
                0 -> topLeftSpring = it
                1 -> topRightSpring = it
                2 -> bottomLeftSpring = it
                3 -> bottomRightSpring = it
            }
        }
    }
    
    private fun playFlashEffect() {
        // Quick white flash
        flashAlpha = 0.8f
        invalidate()
        
        // Fade out flash
        animate()
            .setUpdateListener { flashAlpha = 0.8f * (1f - it.animatedFraction) }
            .setDuration(150)
            .withEndAction { flashAlpha = 0f }
            .start()
    }
    
    private fun triggerImpactFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(20, VibrationEffect.EFFECT_HEAVY_CLICK)
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (e: Exception) {
            // Haptics not available, silently fail
        }
    }
    
    /**
     * Reset to allow re-animation (for testing or re-entry)
     */
    fun reset() {
        hasAnimated = false
        topLeftSpring?.cancel()
        topRightSpring?.cancel()
        bottomLeftSpring?.cancel()
        bottomRightSpring?.cancel()
        
        topLeftProgress = 0f
        topRightProgress = 0f
        bottomLeftProgress = 0f
        bottomRightProgress = 0f
        alpha = 0f
        invalidate()
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        topLeftSpring?.cancel()
        topRightSpring?.cancel()
        bottomLeftSpring?.cancel()
        bottomRightSpring?.cancel()
    }
}
