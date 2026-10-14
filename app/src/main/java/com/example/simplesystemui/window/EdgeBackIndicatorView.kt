package com.example.simplesystemui.window

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** A dark bubble pulled out of the physical left edge, with a white back chevron. */
internal class EdgeBackIndicatorView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val bubble = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arrow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val bubblePath = Path()
    private val arrowPath = Path()
    private var distance = 0f
    private var armed = false

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun update(distancePx: Float, armed: Boolean) {
        distance = distancePx.coerceAtLeast(0f)
        this.armed = armed
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val progress = (distance / (56f * density)).coerceIn(0f, 1f)
        val reach = (18f * density + distance * 0.32f).coerceAtMost(72f * density)
        val halfHeight = (36f + 10f * progress) * density
        val centerY = height / 2f
        bubble.color = if (armed) Color.rgb(36, 48, 65) else Color.rgb(41, 45, 53)
        bubblePath.reset()
        bubblePath.moveTo(0f, centerY - halfHeight)
        bubblePath.cubicTo(0f, centerY - halfHeight * 0.45f,
            reach, centerY - halfHeight * 0.6f, reach, centerY)
        bubblePath.cubicTo(reach, centerY + halfHeight * 0.6f,
            0f, centerY + halfHeight * 0.45f, 0f, centerY + halfHeight)
        bubblePath.close()
        canvas.drawPath(bubblePath, bubble)

        val centerX = reach * 0.6f
        val arrowSize = (if (armed) 9f else 8f) * density
        arrow.strokeWidth = (if (armed) 2.5f else 2.2f) * density
        arrowPath.reset()
        arrowPath.moveTo(centerX + arrowSize * 0.35f, centerY - arrowSize)
        arrowPath.lineTo(centerX - arrowSize * 0.45f, centerY)
        arrowPath.lineTo(centerX + arrowSize * 0.35f, centerY + arrowSize)
        canvas.drawPath(arrowPath, arrow)
    }
}
