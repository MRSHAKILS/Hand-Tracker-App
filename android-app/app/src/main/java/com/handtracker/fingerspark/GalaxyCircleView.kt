package com.handtracker.fingerspark

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class GalaxyCircleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val ringBounds = RectF()
    private var startTime = SystemClock.uptimeMillis()
    private var running = false

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        start()
    }

    override fun onDetachedFromWindow() {
        running = false
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) {
            start()
        } else {
            running = false
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val size = min(w, h)
        val cx = w / 2f
        val cy = h / 2f
        val elapsed = (SystemClock.uptimeMillis() - startTime) / 1000f
        val radius = size * 0.31f

        ringBounds.set(cx - radius, cy - radius, cx + radius, cy + radius)
        drawRing(canvas, elapsed * 22f, 248f, 88f, Color.rgb(33, 115, 86))
        drawRing(canvas, elapsed * -18f, 38f, 48f, Color.rgb(221, 105, 91))

        drawOrbitDot(canvas, cx, cy, radius, elapsed, 0f, Color.rgb(221, 105, 91), 5f)
        drawOrbitDot(canvas, cx, cy, radius * 0.86f, elapsed, 2.1f, Color.rgb(33, 115, 86), 4f)

        if (running) {
            postInvalidateOnAnimation()
        }
    }

    private fun start() {
        running = true
        startTime = SystemClock.uptimeMillis()
        postInvalidateOnAnimation()
    }

    private fun drawRing(canvas: Canvas, rotation: Float, start: Float, sweep: Float, color: Int) {
        ringPaint.color = color
        ringPaint.strokeWidth = width.coerceAtMost(height) * 0.025f
        canvas.save()
        canvas.rotate(rotation, width / 2f, height / 2f)
        canvas.drawArc(ringBounds, start, sweep, false, ringPaint)
        canvas.restore()
    }

    private fun drawOrbitDot(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        elapsed: Float,
        offset: Float,
        color: Int,
        dotRadius: Float
    ) {
        val angle = elapsed * 1.45f + offset
        dotPaint.color = color
        canvas.drawCircle(
            cx + cos(angle) * radius,
            cy + sin(angle) * radius,
            dotRadius,
            dotPaint
        )
    }
}
