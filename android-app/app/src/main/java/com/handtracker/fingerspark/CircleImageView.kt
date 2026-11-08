package com.handtracker.fingerspark

import android.content.Context
import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.widget.ImageView
import kotlin.math.min

// The host uses a framework Material theme and ComponentActivity, not AppCompat inflation.
@SuppressLint("AppCompatCustomView")
class CircleImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ImageView(context, attrs) {
    private val clipPath = Path()
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = SparkStyle.rule
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density
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
