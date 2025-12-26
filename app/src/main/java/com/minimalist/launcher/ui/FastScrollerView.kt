package com.minimalist.launcher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.res.ResourcesCompat
import com.minimalist.launcher.R

class FastScrollerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val alphabet = ('A'..'Z').toList() + '#'
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80FFFFFF") // Semi-transparent white
        textSize = 32f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    
    // Highlight paint for selected letter
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 48f // Larger when selected
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private var letterHeight = 0f
    private var sectionListener: ((String) -> Unit)? = null
    private var currentSection: String? = null

    init {
        // Try to load custom font if available, or just generic sans serif
        try {
            // If you have a custom font:
            // typeface = ResourcesCompat.getFont(context, R.font.your_font)
        } catch (e: Exception) {
            // Ignore
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Calculate dynamic height based on available space, keeping padding
        val availableHeight = h - paddingTop - paddingBottom
        letterHeight = availableHeight.toFloat() / alphabet.size
        
        // Adjust text size dynamically if needed, but 32f is usually safe
        // paint.textSize = letterHeight * 0.7f 
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val xPos = width / 2f
        var yPos = paddingTop + letterHeight / 2f

        alphabet.forEach { char ->
            val charStr = char.toString()
            val isSelected = charStr == currentSection
            
            // Draw letter
            canvas.drawText(
                charStr, 
                xPos, 
                yPos + (if(isSelected) selectedPaint.textSize/3 else paint.textSize/3), // Center vertically roughly
                if (isSelected) selectedPaint else paint
            )
            yPos += letterHeight
        }
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE -> {
                val index = ((event.y - paddingTop) / letterHeight).toInt()
                if (index in alphabet.indices) {
                    val section = alphabet[index].toString()
                    if (section != currentSection) {
                        currentSection = section
                        sectionListener?.invoke(section)
                        invalidate() // Redraw to show selection effect
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                currentSection = null
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun setOnSectionListener(listener: (String) -> Unit) {
        sectionListener = listener
    }
}
