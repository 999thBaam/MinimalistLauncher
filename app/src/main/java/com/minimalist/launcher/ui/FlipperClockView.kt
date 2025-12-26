package com.minimalist.launcher.ui

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.minimalist.launcher.R

/**
 * Composite view that displays a complete clock in HH:MM format using FlipperDigitView components.
 * 
 * Layout: [H1] [H2] : [M1] [M2]
 * The colon is represented by two stacked dots in the center.
 */
class FlipperClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val hourTens: FlipperDigitView
    private val hourOnes: FlipperDigitView
    private val minuteTens: FlipperDigitView
    private val minuteOnes: FlipperDigitView
    
    private var currentHour = -1
    private var currentMinute = -1

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        
        val digitWidth = (64 * resources.displayMetrics.density).toInt()
        val digitHeight = (96 * resources.displayMetrics.density).toInt()
        val digitGap = (4 * resources.displayMetrics.density).toInt()
        val colonWidth = (24 * resources.displayMetrics.density).toInt()
        
        // Hour tens digit
        hourTens = FlipperDigitView(context).apply {
            layoutParams = LayoutParams(digitWidth, digitHeight).apply {
                marginEnd = digitGap
            }
        }
        addView(hourTens)
        
        // Hour ones digit
        hourOnes = FlipperDigitView(context).apply {
            layoutParams = LayoutParams(digitWidth, digitHeight)
        }
        addView(hourOnes)
        
        // Colon separator (two dots)
        addView(createColonView(colonWidth, digitHeight))
        
        // Minute tens digit
        minuteTens = FlipperDigitView(context).apply {
            layoutParams = LayoutParams(digitWidth, digitHeight).apply {
                marginEnd = digitGap
            }
        }
        addView(minuteTens)
        
        // Minute ones digit
        minuteOnes = FlipperDigitView(context).apply {
            layoutParams = LayoutParams(digitWidth, digitHeight)
        }
        addView(minuteOnes)
    }
    
    private fun createColonView(width: Int, height: Int): LinearLayout {
        return LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(width, height)
            
            val dotSize = (8 * resources.displayMetrics.density).toInt()
            val dotGap = (24 * resources.displayMetrics.density).toInt()
            
            // Top dot
            addView(android.view.View(context).apply {
                layoutParams = LayoutParams(dotSize, dotSize).apply {
                    bottomMargin = dotGap / 2
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(ContextCompat.getColor(context, R.color.dot_indicator))
                }
            })
            
            // Bottom dot
            addView(android.view.View(context).apply {
                layoutParams = LayoutParams(dotSize, dotSize).apply {
                    topMargin = dotGap / 2
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(ContextCompat.getColor(context, R.color.dot_indicator))
                }
            })
        }
    }

    /**
     * Set the time to display.
     * @param hour Hour in 24-hour format (0-23)
     * @param minute Minute (0-59)
     * @param animate Whether to animate the digit changes
     */
    fun setTime(hour: Int, minute: Int, animate: Boolean = true) {
        if (hour == currentHour && minute == currentMinute) return
        
        val h = hour.coerceIn(0, 23)
        val m = minute.coerceIn(0, 59)
        
        // Update hour digits
        if (h != currentHour) {
            hourTens.setDigit(h / 10, animate && currentHour >= 0)
            hourOnes.setDigit(h % 10, animate && currentHour >= 0)
            currentHour = h
        }
        
        // Update minute digits
        if (m != currentMinute) {
            minuteTens.setDigit(m / 10, animate && currentMinute >= 0)
            minuteOnes.setDigit(m % 10, animate && currentMinute >= 0)
            currentMinute = m
        }
    }
    
    /**
     * Set time from a formatted string like "23:45"
     */
    fun setTimeFromString(timeString: String, animate: Boolean = true) {
        val parts = timeString.split(":")
        if (parts.size == 2) {
            val hour = parts[0].toIntOrNull() ?: 0
            val minute = parts[1].toIntOrNull() ?: 0
            setTime(hour, minute, animate)
        }
    }
}
