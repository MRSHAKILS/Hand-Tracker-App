package com.handtracker.fingerspark

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class SparkBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val handPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 255, 248, 216)
        style = Paint.Style.STROKE
        strokeWidth = 7f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 216, 77)
        style = Paint.Style.FILL
        setShadowLayer(18f, 0f, 0f, Color.rgb(255, 91, 154))
    }
    private val path = Path()
    private var startTime = SystemClock.uptimeMillis()
    private var running = false

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        running = true
        startTime = SystemClock.uptimeMillis()
        postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        running = false
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        running = visibility == VISIBLE
        if (running) {
            postInvalidateOnAnimation()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val t = ((SystemClock.uptimeMillis() - startTime) % 5000L) / 5000f

        backgroundPaint.shader = LinearGradient(
            0f,
            0f,
            w,
            h,
            intArrayOf(
                Color.rgb(23, 34, 47),
                Color.rgb(20, 77, 90),
                Color.rgb(14, 44, 64)
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, backgroundPaint)

        drawGlow(canvas, w * 0.22f, h * 0.18f, min(w, h) * 0.38f, Color.argb(95, 19, 200, 192))
        drawGlow(canvas, w * 0.82f, h * 0.24f, min(w, h) * 0.28f, Color.argb(75, 255, 91, 154))
        drawHand(canvas, w, h, t)
        drawSparks(canvas, w, h, t)

        if (running) {
            postInvalidateOnAnimation()
        }
    }

    private fun drawGlow(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        glowPaint.shader = RadialGradient(x, y, radius, color, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawCircle(x, y, radius, glowPaint)
        glowPaint.shader = null
    }

    private fun drawHand(canvas: Canvas, w: Float, h: Float, t: Float) {
        val size = min(w, h) * 0.5f
        val cx = w * 0.5f
        val cy = h * 0.43f + sin(t * FULL_TURN) * 8f
        val top = cy - size * 0.32f
        val palmTop = cy + size * 0.05f
        val palmBottom = cy + size * 0.42f
        val gap = size * 0.12f

        path.reset()
        path.moveTo(cx - gap * 1.5f, palmTop)
        path.lineTo(cx - gap * 1.5f, top + size * 0.26f)
        path.quadTo(cx - gap * 1.5f, top + size * 0.14f, cx - gap * 0.8f, top + size * 0.14f)
        path.quadTo(cx - gap * 0.1f, top + size * 0.14f, cx - gap * 0.1f, top + size * 0.28f)
        path.lineTo(cx - gap * 0.1f, top + size * 0.06f)
        path.quadTo(cx - gap * 0.1f, top - size * 0.08f, cx + gap * 0.65f, top - size * 0.08f)
        path.quadTo(cx + gap * 1.4f, top - size * 0.08f, cx + gap * 1.4f, top + size * 0.08f)
        path.lineTo(cx + gap * 1.4f, top + size * 0.34f)
        path.quadTo(cx + gap * 2.7f, top + size * 0.44f, cx + gap * 2.2f, palmTop)
        path.lineTo(cx + gap * 1.65f, palmBottom)
        path.quadTo(cx + gap * 0.2f, palmBottom + size * 0.12f, cx - gap * 1.8f, palmBottom)
        path.lineTo(cx - gap * 2.8f, palmTop + size * 0.2f)
        path.quadTo(cx - gap * 3.2f, palmTop + size * 0.1f, cx - gap * 2.7f, palmTop - size * 0.02f)
        path.quadTo(cx - gap * 2.2f, palmTop - size * 0.1f, cx - gap * 1.5f, palmTop)
        canvas.drawPath(path, handPaint)
    }

    private fun drawSparks(canvas: Canvas, w: Float, h: Float, t: Float) {
        val cx = w * 0.55f
        val cy = h * 0.3f + sin(t * FULL_TURN) * 8f
        val baseRadius = min(w, h) * 0.17f

        for (index in 0 until 9) {
            val angle = FULL_TURN * (t + index / 9f)
            val radius = baseRadius + sin(angle * 1.7f) * 18f
            val x = cx + cos(angle) * radius
            val y = cy + sin(angle) * radius * 0.58f
            val dot = 4f + (index % 3) * 1.8f
            sparkPaint.alpha = 130 + ((sin(angle) + 1f) * 45f).toInt()
            canvas.drawCircle(x, y, dot, sparkPaint)
        }

        sparkPaint.alpha = 255
        canvas.drawCircle(cx, cy, 13f + sin(t * FULL_TURN) * 2f, sparkPaint)
    }

    companion object {
        private const val FULL_TURN = (Math.PI * 2).toFloat()
    }
}
