package com.handtracker.fingerspark

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.min

internal class ModeTileView @JvmOverloads constructor(context: Context, private val isPaint: Boolean = true) : LinearLayout(context) {
    private val artwork = ModeArtworkView(context, isPaint)
    private val copyStack = LinearLayout(context).apply { orientation = VERTICAL }
    private val titleView = TextView(context).apply {
        textSize = 22f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(SparkStyle.ink)
    }
    private val subtitleView = TextView(context).apply {
        textSize = 14f
        setTextColor(SparkStyle.secondary)
    }
    private var compact: Boolean? = null

    init {
        isClickable = true
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        background = SparkStyle.touchSurface(if (isPaint) SparkStyle.mint else SparkStyle.coral, dp(8).toFloat())
        setPadding(dp(18), dp(16), dp(18), dp(18))
        gravity = Gravity.CENTER_VERTICAL
        copyStack.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        val titleRow = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(titleView, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        titleRow.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_arrow_forward)
            setColorFilter(if (isPaint) SparkStyle.paintAccent else SparkStyle.gameAccent)
        }, LayoutParams(dp(20), dp(20)).apply { marginStart = dp(4) })
        copyStack.addView(titleRow)
        copyStack.addView(subtitleView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(4)
        })
        addView(artwork)
        addView(copyStack)
        setCompact(false)
    }

    fun bindText(title: String, subtitle: String) {
        titleView.text = title
        subtitleView.text = subtitle
        contentDescription = "$title. $subtitle"
    }

    fun setCompact(value: Boolean) {
        if (compact == value) return
        compact = value
        orientation = if (value) HORIZONTAL else VERTICAL
        minimumHeight = dp(if (value) 112 else 196)
        artwork.layoutParams = LayoutParams(if (value) dp(76) else LayoutParams.MATCH_PARENT, dp(if (value) 72 else 84)).apply {
            marginEnd = if (value) dp(14) else 0
            bottomMargin = if (value) 0 else dp(12)
        }
        copyStack.layoutParams = if (value) LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            else LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.Button"
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

private class ModeArtworkView @JvmOverloads constructor(context: Context, private val isPaint: Boolean = true) : View(context) {
    private val brush = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val shape = Path()

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = min(width / 148f, height / 88f)
        canvas.save()
        canvas.translate((width - 148f * scale) / 2f, (height - 88f * scale) / 2f)
        canvas.scale(scale, scale)
        brush.style = Paint.Style.STROKE
        if (isPaint) {
            brush.strokeWidth = 12f
            brush.color = SparkStyle.paintAccent
            shape.reset()
            shape.moveTo(17f, 61f)
            shape.cubicTo(33f, 8f, 59f, 12f, 53f, 44f)
            shape.cubicTo(44f, 85f, 76f, 64f, 83f, 38f)
            canvas.drawPath(shape, brush)
            brush.color = SparkStyle.gameAccent
            brush.strokeWidth = 8f
            shape.reset()
            shape.moveTo(87f, 65f)
            shape.quadTo(104f, 72f, 124f, 48f)
            canvas.drawPath(shape, brush)
            brush.color = SparkStyle.ink
            brush.strokeWidth = 2f
            canvas.drawCircle(83f, 38f, 11f, brush)
        } else {
            brush.color = SparkStyle.gameAccent
            brush.strokeWidth = 2.5f
            canvas.drawCircle(104f, 29f, 22f, brush)
            canvas.drawCircle(104f, 29f, 14f, brush)
            brush.style = Paint.Style.FILL
            canvas.drawCircle(104f, 29f, 6f, brush)
            brush.color = SparkStyle.paintAccent
            canvas.drawCircle(33f, 67f, 9f, brush)
            brush.style = Paint.Style.STROKE
            brush.strokeWidth = 3f
            shape.reset()
            shape.moveTo(46f, 64f)
            shape.cubicTo(61f, 63f, 62f, 38f, 76f, 34f)
            canvas.drawPath(shape, brush)
            shape.reset()
            shape.moveTo(70f, 29f)
            shape.lineTo(79f, 33f)
            shape.lineTo(75f, 42f)
            canvas.drawPath(shape, brush)
        }
        canvas.restore()
    }
}
