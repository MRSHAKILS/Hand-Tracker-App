package com.handtracker.fingerspark

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.widget.ImageView
import kotlin.math.min

class CircleImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ImageView(context, attrs) {
    private val clipPath = Path()
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 248, 216)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    override fun onDraw(canvas: Canvas) {
        val radius = min(width, height) / 2f
        clipPath.reset()
        clipPath.addCircle(width / 2f, height / 2f, radius - borderPaint.strokeWidth, Path.Direction.CW)

        canvas.save()
        canvas.clipPath(clipPath)
        super.onDraw(canvas)
        canvas.restore()

        canvas.drawCircle(width / 2f, height / 2f, radius - borderPaint.strokeWidth / 2f, borderPaint)
    }
}
