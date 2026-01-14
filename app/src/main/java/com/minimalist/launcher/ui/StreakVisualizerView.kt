package com.minimalist.launcher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.minimalist.launcher.R

/**
 * Custom View that displays a GitHub-style contribution/streak graph.
 * Shows the last 7 days as colored squares based on task completion rate.
 */
class StreakVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Data: day offset (0=today) to completion count
    private var completionData: Map<Int, Int> = emptyMap()
    
    // Paints for different intensity levels
    private val paints = mutableListOf<Paint>()
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.secondaryText)
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }
    
    // Square dimensions
    private val squareSize = 40f
    private val squareSpacing = 12f
    private val cornerRadius = 6f
    
    // Day labels
    private val dayLabels = listOf("Today", "1d", "2d", "3d", "4d", "5d", "6d")
    
    init {
        // Create gradient paints from empty to full (grayscale to match minimalist theme)
        val colors = listOf(
            0xFF1A1A1A.toInt(), // Level 0: Nearly black (no activity)
            0xFF2D2D2D.toInt(), // Level 1: Dark gray
            0xFF4A4A4A.toInt(), // Level 2: Medium-dark gray
            0xFF6A6A6A.toInt(), // Level 3: Medium gray
            0xFF8A8A8A.toInt()  // Level 4: Light gray (high activity)
        )
        
        colors.forEach { color ->
            paints.add(Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.FILL
            })
        }
    }
    
    /**
     * Sets the completion data and triggers a redraw.
     * @param data Map of day offset (0=today, 6=6 days ago) to completion count
     */
    fun setCompletionData(data: Map<Int, Int>) {
        completionData = data
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val totalWidth = 7 * squareSize + 6 * squareSpacing
        val startX = (width - totalWidth) / 2
        val topY = 8f
        
        for (i in 0 until 7) {
            val count = completionData[i] ?: 0
            val level = when {
                count == 0 -> 0
                count == 1 -> 1
                count == 2 -> 2
                count <= 4 -> 3
                else -> 4
            }
            
            val left = startX + i * (squareSize + squareSpacing)
            val rect = RectF(left, topY, left + squareSize, topY + squareSize)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paints[level])
            
            // Draw day label below
            if (i == 0) {
                canvas.drawText("T", left + squareSize / 2, topY + squareSize + 24f, labelPaint)
            }
        }
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = (7 * squareSize + 6 * squareSpacing + paddingLeft + paddingRight).toInt()
        val desiredHeight = (squareSize + 32f + paddingTop + paddingBottom).toInt()
        
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        
        val width = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST -> minOf(desiredWidth, widthSize)
            else -> desiredWidth
        }
        
        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> minOf(desiredHeight, heightSize)
            else -> desiredHeight
        }
        
        setMeasuredDimension(width, height)
    }
}
