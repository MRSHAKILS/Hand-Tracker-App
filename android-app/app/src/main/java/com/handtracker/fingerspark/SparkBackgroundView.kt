package com.handtracker.fingerspark

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class SparkBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
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

        if (running) {
            postInvalidateOnAnimation()
        }
    }

    private fun drawGlow(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        glowPaint.shader = RadialGradient(x, y, radius, color, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawCircle(x, y, radius, glowPaint)
        glowPaint.shader = null
    }
}
