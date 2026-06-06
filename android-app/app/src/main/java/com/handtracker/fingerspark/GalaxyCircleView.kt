package com.handtracker.fingerspark

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
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
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(24f, BlurMaskFilter.Blur.NORMAL)
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
        val radius = size * 0.34f

        glowPaint.shader = RadialGradient(
            cx,
            cy,
            size * 0.48f,
            intArrayOf(
                Color.argb(90, 63, 220, 202),
                Color.argb(42, 255, 112, 166),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, size * 0.43f, glowPaint)
        glowPaint.shader = null

        ringBounds.set(cx - radius, cy - radius, cx + radius, cy + radius)
        drawRing(canvas, elapsed * 28f, 255f, 85f, Color.argb(175, 91, 231, 219))
        drawRing(canvas, elapsed * -36f, 42f, 72f, Color.argb(155, 255, 142, 181))
        drawRing(canvas, elapsed * 20f, 176f, 58f, Color.argb(120, 255, 230, 139))

        drawOrbitDot(canvas, cx, cy, radius, elapsed, 0f, Color.argb(240, 255, 244, 171), 5.5f)
        drawOrbitDot(canvas, cx, cy, radius * 0.82f, elapsed, 2.2f, Color.argb(235, 125, 242, 224), 4.5f)
        drawOrbitDot(canvas, cx, cy, radius * 1.05f, elapsed, 4.1f, Color.argb(225, 255, 143, 176), 4f)

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
