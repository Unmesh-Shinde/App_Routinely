package com.dailyroutine.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

data class GoalLineSpec(
    val value: Double,
    val maxValue: Double,
    val label: String,
    val color: Int
)

class GoalLineOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.4f * resources.displayMetrics.density
        pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10f, resources.displayMetrics)
        isFakeBoldText = true
    }

    private var goalLines: List<GoalLineSpec> = emptyList()

    fun setGoalLines(lines: List<GoalLineSpec>) {
        goalLines = lines
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (goalLines.isEmpty() || width <= 0 || height <= 0) return

        val drawableHeight = height.toFloat().coerceAtLeast(1f)

        goalLines.forEach { spec ->
            if (spec.maxValue <= 0.0) return@forEach
            val ratio = (spec.value / spec.maxValue).coerceIn(0.0, 1.0)
            val y = (drawableHeight - (drawableHeight * ratio)).toFloat()
            linePaint.color = spec.color
            linePaint.alpha = 230
            labelPaint.color = spec.color
            canvas.drawLine(0f, y, width.toFloat(), y, linePaint)
            
            // Fix: Shift label downward if line is at the very top to prevent clipping
            val labelY = if (y < 30f) y + 24f else y - 4f
            canvas.drawText(spec.label, 8f * resources.displayMetrics.density, labelY, labelPaint)
        }
    }
}
