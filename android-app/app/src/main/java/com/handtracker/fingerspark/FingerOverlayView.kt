package com.handtracker.fingerspark

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

class FingerOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 216, 77)
        style = Paint.Style.FILL
        setShadowLayer(28f, 0f, 0f, Color.rgb(255, 91, 154))
    }

    private var fingertipX: Float? = null
    private var fingertipY: Float? = null
    private var sourceWidth = 1
    private var sourceHeight = 1

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setFingertip(x: Float, y: Float, imageWidth: Int, imageHeight: Int) {
        fingertipX = x
        fingertipY = y
        sourceWidth = max(imageWidth, 1)
        sourceHeight = max(imageHeight, 1)
        invalidate()
    }

    fun clearFingertip() {
        fingertipX = null
        fingertipY = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val normalizedX = fingertipX ?: return
        val normalizedY = fingertipY ?: return
        val scale = max(width / sourceWidth.toFloat(), height / sourceHeight.toFloat())
        val drawnWidth = sourceWidth * scale
        val drawnHeight = sourceHeight * scale
        val offsetX = (width - drawnWidth) / 2f
        val offsetY = (height - drawnHeight) / 2f
        val x = offsetX + normalizedX * drawnWidth
        val y = offsetY + normalizedY * drawnHeight

        canvas.drawCircle(x, y, 18f, dotPaint)
    }
}
