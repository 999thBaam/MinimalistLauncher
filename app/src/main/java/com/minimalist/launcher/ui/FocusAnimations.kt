package com.minimalist.launcher.ui

import android.animation.ValueAnimator
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator

/**
 * Premium entrance animations for the Focus Launcher.
 * "Optical Focus" effect: Content starts blurred and scales into crisp focus.
 */
object FocusAnimations {
    
    /**
     * The signature "Optical Focus" animation.
     * Content starts blurred (like unfocused lens) and sharpens into clarity.
     * 
     * @param target The view to animate
     * @param duration Animation duration in ms (default 800)
     * @param startDelay Delay before starting (for staggering)
     */
    fun playOpticalFocus(
        target: View,
        duration: Long = 800,
        startDelay: Long = 0
    ) {
        // Initial state: slightly scaled up, transparent
        target.alpha = 0f
        target.scaleX = 1.05f
        target.scaleY = 1.05f
        
        // Blur animation (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Start with heavy blur
            applyBlur(target, 25f)
            
            ValueAnimator.ofFloat(25f, 0f).apply {
                this.duration = duration
                this.startDelay = startDelay
                interpolator = FastOutSlowInInterpolator()
                
                addUpdateListener { animator ->
                    val blurRadius = animator.animatedValue as Float
                    if (blurRadius > 0.5f) {
                        applyBlur(target, blurRadius)
                    } else {
                        target.setRenderEffect(null)
                    }
                }
                start()
            }
        }
        
        // Fade in + scale down
        target.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(duration)
            .setStartDelay(startDelay)
            .setInterpolator(OvershootInterpolator(0.5f))
            .start()
    }
    
    /**
     * Staggered entrance: Icon snaps, then title slides, then body slides.
     * Creates a premium "cascade" effect.
     */
    fun playStaggeredEntrance(
        icon: View,
        title: View,
        body: View,
        baseDuration: Long = 500
    ) {
        // Reset initial states
        listOf(icon, title, body).forEach { view ->
            view.alpha = 0f
            view.translationY = 30f
        }
        
        // Icon: snap with slight overshoot
        icon.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(baseDuration)
            .setInterpolator(OvershootInterpolator(1.2f))
            .start()
        
        // Title: slide up with delay
        title.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(baseDuration)
            .setStartDelay(100)
            .setInterpolator(FastOutSlowInInterpolator())
            .start()
        
        // Body: slide up with more delay
        body.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(baseDuration)
            .setStartDelay(200)
            .setInterpolator(FastOutSlowInInterpolator())
            .start()
    }
    
    /**
     * Combined effect: Optical focus on container + staggered children
     */
    fun playFocusEntrance(
        container: View,
        icon: View,
        title: View,
        body: View
    ) {
        // Apply blur to container
        playOpticalFocus(container, duration = 600)
        
        // Stagger the children slightly after
        icon.postDelayed({
            playStaggeredEntrance(icon, title, body, baseDuration = 400)
        }, 100)
    }
    
    /**
     * Simple fade-in for API < 31 fallback
     */
    fun playSimpleFadeIn(target: View, duration: Long = 500, startDelay: Long = 0) {
        target.alpha = 0f
        target.animate()
            .alpha(1f)
            .setDuration(duration)
            .setStartDelay(startDelay)
            .setInterpolator(FastOutSlowInInterpolator())
            .start()
    }
    
    /**
     * Apply blur effect (API 31+)
     */
    private fun applyBlur(view: View, radius: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && radius > 0f) {
            view.setRenderEffect(
                RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
            )
        }
    }
    
    /**
     * "Zen Pulse" breathing animation for icons
     */
    fun startBreathingPulse(target: View, cycleDuration: Long = 4000) {
        target.animate()
            .scaleX(1.03f)
            .scaleY(1.03f)
            .setDuration(cycleDuration / 2)
            .withEndAction {
                target.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(cycleDuration / 2)
                    .withEndAction {
                        startBreathingPulse(target, cycleDuration)
                    }
                    .start()
            }
            .start()
    }
    
    fun stopBreathingPulse(target: View) {
        target.animate().cancel()
        target.scaleX = 1f
        target.scaleY = 1f
    }
}
