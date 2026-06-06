package com.handtracker.fingerspark

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import java.util.Random
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class FingerOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        setShadowLayer(28f, 0f, 0f, Color.rgb(255, 91, 154))
    }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(19, 200, 192)
        style = Paint.Style.FILL
        setShadowLayer(24f, 0f, 0f, Color.rgb(255, 216, 77))
    }
    private val targetRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 255, 248, 216)
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 248, 216)
        textSize = 48f
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(8f, 0f, 4f, Color.argb(180, 0, 0, 0))
    }
    private val palettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val paletteRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 91, 154)
        style = Paint.Style.STROKE
        strokeWidth = 7f
        strokeCap = Paint.Cap.ROUND
    }

    private val palette = listOf(
        Swatch(Color.rgb(255, 82, 82), Color.rgb(255, 177, 177)),
        Swatch(Color.rgb(255, 156, 58), Color.rgb(255, 207, 149)),
        Swatch(Color.rgb(255, 216, 77), Color.rgb(255, 243, 158)),
        Swatch(Color.rgb(126, 217, 87), Color.rgb(197, 240, 168)),
        Swatch(Color.rgb(19, 200, 192), Color.rgb(133, 235, 229)),
        Swatch(Color.rgb(95, 168, 255), Color.rgb(170, 203, 255)),
        Swatch(Color.rgb(178, 102, 255), Color.rgb(216, 179, 255)),
        Swatch(Color.rgb(255, 91, 154), Color.rgb(255, 172, 201)),
        Swatch(Color.rgb(255, 248, 216), Color.WHITE)
    )
    private val swatchRects = mutableListOf<RectF>()
    private val trail = ArrayDeque<FingertipPoint>()
    private val random = Random()

    private var paintBitmap: Bitmap? = null
    private var paintCanvas: Canvas? = null
    private var fingertipX: Float? = null
    private var fingertipY: Float? = null
    private var sourceWidth = 1
    private var sourceHeight = 1
    private var targetX = 0.68f
    private var targetY = 0.34f
    private var score = 0
    private var lastCaptureMs = 0L
    private var activeColorIndex = 2
    private var hoveredSwatchIndex: Int? = null
    private var swatchHoldStartedAt: Long? = null
    private var clearHoldStartedAt: Long? = null
    private var smoothedHandSize: Float? = null
    private var brushRadius = (MIN_BRUSH_RADIUS + MAX_BRUSH_RADIUS) / 2f
    private var brushSpacing = brushRadius * SPACING_PER_RADIUS
    private var penActive = false
    private var previousPoint: PointF? = null
    private var distanceLeft = 0f
    private var cursorMode = CursorMode.HOVER
    private var clearProgress = 0f
    private var playMode = PlayMode.PAINT
    var onSparkCaptured: (() -> Unit)? = null

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setPlayMode(mode: PlayMode) {
        playMode = mode
        clearFingertip()
        invalidate()
    }

    fun setHandState(
        x: Float,
        y: Float,
        imageWidth: Int,
        imageHeight: Int,
        indexUp: Boolean,
        middleUp: Boolean,
        ringUp: Boolean,
        pinkyUp: Boolean,
        handSize: Float
    ) {
        fingertipX = x
        fingertipY = y
        sourceWidth = max(imageWidth, 1)
        sourceHeight = max(imageHeight, 1)
        updateBrush(handSize)

        val mapped = mapPoint(x, y) ?: run {
            invalidate()
            return
        }
        val isClearGesture = indexUp && middleUp && ringUp && pinkyUp
        val shouldDraw = indexUp && !middleUp && !isClearGesture

        if (playMode == PlayMode.GAME) {
            trail.addLast(FingertipPoint(x, y))
            while (trail.size > MAX_TRAIL_POINTS) {
                trail.removeFirst()
            }
            captureTargetIfTouched(x, y)
            invalidate()
            return
        }

        if (!isClearGesture && findSwatchAt(mapped) != null) {
            clearHoldStartedAt = null
            clearProgress = 0f
            liftPen()
            handlePaletteHover(mapped)
            trail.addLast(FingertipPoint(x, y))
            while (trail.size > MAX_TRAIL_POINTS) {
                trail.removeFirst()
            }
            invalidate()
            return
        }

        if (isClearGesture) {
            liftPen()
            resetSwatchHold()
            updateClearGesture()
            cursorMode = CursorMode.CLEAR
        } else {
            clearHoldStartedAt = null
            clearProgress = 0f
            if (shouldDraw) {
                resetSwatchHold()
                drawPaintDot(mapped)
                cursorMode = CursorMode.DRAW
            } else {
                liftPen()
                handlePaletteHover(mapped)
            }
        }

        trail.addLast(FingertipPoint(x, y))
        while (trail.size > MAX_TRAIL_POINTS) {
            trail.removeFirst()
        }
        invalidate()
    }

    fun clearFingertip() {
        fingertipX = null
        fingertipY = null
        trail.clear()
        liftPen()
        resetSwatchHold()
        clearHoldStartedAt = null
        clearProgress = 0f
        invalidate()
    }

    fun resetGame() {
        score = 0
        lastCaptureMs = 0L
        smoothedHandSize = null
        moveTarget()
        clearDrawing()
        clearFingertip()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width <= 0 || height <= 0) {
            return
        }

        val oldBitmap = paintBitmap
        val newBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val newCanvas = Canvas(newBitmap)
        oldBitmap?.let { newCanvas.drawBitmap(it, 0f, 0f, null) }
        paintBitmap = newBitmap
        paintCanvas = newCanvas
        oldBitmap?.recycle()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (playMode == PlayMode.PAINT) {
            paintBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        }

        val scale = max(width / sourceWidth.toFloat(), height / sourceHeight.toFloat())
        val drawnWidth = sourceWidth * scale
        val drawnHeight = sourceHeight * scale
        val offsetX = (width - drawnWidth) / 2f
        val offsetY = (height - drawnHeight) / 2f

        if (playMode == PlayMode.PAINT) {
            drawPalette(canvas)
        } else {
            drawScore(canvas)
            drawTarget(canvas, offsetX, offsetY, drawnWidth, drawnHeight)
        }

        val normalizedX = fingertipX ?: return
        val normalizedY = fingertipY ?: return
        val x = offsetX + normalizedX * drawnWidth
        val y = offsetY + normalizedY * drawnHeight

        trail.forEachIndexed { index, point ->
            val alpha = (32 + index * 18).coerceIn(32, 180)
            val radius = (8f + index * 1.4f).coerceAtMost(16f)
            trailPaint.color = palette[(activeColorIndex + index) % palette.size].color
            trailPaint.alpha = alpha
            canvas.drawCircle(
                offsetX + point.x * drawnWidth,
                offsetY + point.y * drawnHeight,
                radius,
                trailPaint
            )
        }
        trailPaint.alpha = 255
        if (playMode == PlayMode.PAINT) {
            drawCursor(canvas, x, y)
        } else {
            drawGameCursor(canvas, x, y)
        }
    }

    private fun updateBrush(handSize: Float) {
        val current = smoothedHandSize
        smoothedHandSize = if (current == null) {
            handSize
        } else {
            current * (1f - BRUSH_SMOOTHING) + handSize * BRUSH_SMOOTHING
        }

        val size = smoothedHandSize ?: handSize
        val t = ((size - MIN_HAND_SIZE) / (MAX_HAND_SIZE - MIN_HAND_SIZE)).coerceIn(0f, 1f)
        brushRadius = MIN_BRUSH_RADIUS + (MAX_BRUSH_RADIUS - MIN_BRUSH_RADIUS) * t
        brushSpacing = max(2f, brushRadius * SPACING_PER_RADIUS)
    }

    private fun drawPaintDot(point: PointF) {
        val canvas = paintCanvas ?: return
        val swatch = palette[activeColorIndex]
        dotPaint.color = swatch.color
        dotPaint.setShadowLayer(max(8f, brushRadius * 2f), 0f, 0f, swatch.glow)

        if (!penActive) {
            penActive = true
            previousPoint = point
            distanceLeft = 0f
            canvas.drawCircle(point.x, point.y, brushRadius, dotPaint)
            return
        }

        val previous = previousPoint ?: point
        val distance = hypot(point.x - previous.x, point.y - previous.y)
        val steps = max(1, ceil(distance / 2f).toInt())

        for (step in 1..steps) {
            val t = step / steps.toFloat()
            val x = previous.x + (point.x - previous.x) * t
            val y = previous.y + (point.y - previous.y) * t
            distanceLeft += hypot(x - previous.x, y - previous.y)
            if (distanceLeft >= brushSpacing) {
                canvas.drawCircle(x, y, brushRadius, dotPaint)
                distanceLeft = 0f
            }
        }
        previousPoint = point
    }

    private fun liftPen() {
        penActive = false
        previousPoint = null
        distanceLeft = 0f
    }

    private fun handlePaletteHover(point: PointF) {
        val swatchIndex = findSwatchAt(point)
        if (swatchIndex == null || swatchIndex == activeColorIndex) {
            resetSwatchHold()
            cursorMode = if (swatchIndex == null) CursorMode.HOVER else CursorMode.SWATCH
            return
        }

        if (hoveredSwatchIndex != swatchIndex) {
            hoveredSwatchIndex = swatchIndex
            swatchHoldStartedAt = SystemClock.uptimeMillis()
        }

        val elapsed = SystemClock.uptimeMillis() - (swatchHoldStartedAt ?: SystemClock.uptimeMillis())
        if (elapsed >= SWATCH_HOLD_MS) {
            activeColorIndex = swatchIndex
            resetSwatchHold()
        }
        cursorMode = CursorMode.SWATCH
    }

    private fun resetSwatchHold() {
        hoveredSwatchIndex = null
        swatchHoldStartedAt = null
    }

    private fun updateClearGesture() {
        val now = SystemClock.uptimeMillis()
        val startedAt = clearHoldStartedAt ?: now.also { clearHoldStartedAt = it }
        clearProgress = ((now - startedAt).toFloat() / CLEAR_HOLD_MS).coerceIn(0f, 1f)
        if (clearProgress >= 1f) {
            clearDrawing()
            clearHoldStartedAt = null
            clearProgress = 0f
        }
    }

    private fun clearDrawing() {
        paintBitmap?.eraseColor(Color.TRANSPARENT)
    }

    private fun drawPalette(canvas: Canvas) {
        rebuildSwatchRects()
        val radius = currentSwatchRadius()

        palette.forEachIndexed { index, swatch ->
            val rect = swatchRects[index]
            val cx = rect.centerX()
            val cy = rect.centerY()

            palettePaint.color = Color.argb(92, 23, 34, 47)
            canvas.drawCircle(cx, cy, radius + 10f, palettePaint)
            palettePaint.color = swatch.color
            canvas.drawCircle(cx, cy, radius, palettePaint)

            if (index == activeColorIndex) {
                paletteRingPaint.color = swatch.glow
                paletteRingPaint.alpha = 255
                canvas.drawCircle(cx, cy, radius + 7f, paletteRingPaint)
            } else if (index == hoveredSwatchIndex) {
                paletteRingPaint.color = Color.rgb(255, 248, 216)
                paletteRingPaint.alpha = 190
                canvas.drawCircle(cx, cy, radius + 6f, paletteRingPaint)
            }
        }
    }

    private fun findSwatchAt(point: PointF): Int? {
        rebuildSwatchRects()
        return swatchRects.indexOfFirst { it.contains(point.x, point.y) }.takeIf { it >= 0 }
    }

    private fun rebuildSwatchRects() {
        if (width <= 0 || height <= 0) {
            return
        }

        swatchRects.clear()
        val radius = currentSwatchRadius()
        val gap = radius * 0.62f
        val left = max(14f, width * 0.018f)
        val totalHeight = palette.size * radius * 2f + (palette.size - 1) * gap
        val top = (height - totalHeight) / 2f

        palette.indices.forEach { index ->
            val cx = left + radius
            val cy = top + radius + index * (radius * 2f + gap)
            swatchRects.add(RectF(cx - radius, cy - radius, cx + radius, cy + radius))
        }
    }

    private fun currentSwatchRadius(): Float {
        return (min(width, height) * 0.021f).coerceIn(18f, 30f)
    }

    private fun drawCursor(canvas: Canvas, x: Float, y: Float) {
        when (cursorMode) {
            CursorMode.DRAW -> {
                val swatch = palette[activeColorIndex]
                dotPaint.color = swatch.color
                dotPaint.setShadowLayer(max(14f, brushRadius * 2.2f), 0f, 0f, swatch.glow)
                dotPaint.alpha = 215
                canvas.drawCircle(x, y, brushRadius + 4f, dotPaint)
                dotPaint.alpha = 255
            }

            CursorMode.CLEAR -> {
                trailPaint.color = Color.argb(46, 255, 91, 154)
                canvas.drawCircle(x, y, 52f, trailPaint)
                canvas.drawArc(RectF(x - 42f, y - 42f, x + 42f, y + 42f), -90f, 360f * clearProgress, false, clearPaint)
            }

            CursorMode.SWATCH -> {
                trailPaint.color = Color.rgb(255, 248, 216)
                canvas.drawCircle(x, y, 6f, trailPaint)
            }

            CursorMode.HOVER -> {
                cursorPaint.color = Color.rgb(19, 200, 192)
                canvas.drawCircle(x, y, max(14f, brushRadius + 5f), cursorPaint)
                trailPaint.color = Color.rgb(19, 200, 192)
                canvas.drawCircle(x, y, 4f, trailPaint)
            }
        }
    }

    private fun drawGameCursor(canvas: Canvas, x: Float, y: Float) {
        dotPaint.color = Color.rgb(255, 216, 77)
        dotPaint.setShadowLayer(28f, 0f, 0f, Color.rgb(255, 91, 154))
        canvas.drawCircle(x, y, 18f, dotPaint)
    }

    private fun drawScore(canvas: Canvas) {
        canvas.drawText("Spark $score", 28f, 68f, scorePaint)
    }

    private fun drawTarget(
        canvas: Canvas,
        offsetX: Float,
        offsetY: Float,
        drawnWidth: Float,
        drawnHeight: Float
    ) {
        val pulse = ((SystemClock.uptimeMillis() % 900L) / 900f)
        val radius = 26f + pulse * 7f
        val x = offsetX + targetX * drawnWidth
        val y = offsetY + targetY * drawnHeight
        targetPaint.alpha = 170
        canvas.drawCircle(x, y, radius, targetPaint)
        targetRingPaint.alpha = 190
        canvas.drawCircle(x, y, radius + 11f, targetRingPaint)
        postInvalidateOnAnimation()
    }

    private fun captureTargetIfTouched(x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        val dx = x - targetX
        val dy = y - targetY
        if (sqrt(dx * dx + dy * dy) <= TARGET_RADIUS && now - lastCaptureMs > CAPTURE_COOLDOWN_MS) {
            score += 1
            lastCaptureMs = now
            moveTarget()
            onSparkCaptured?.invoke()
        }
    }

    private fun moveTarget() {
        targetX = 0.18f + random.nextFloat() * 0.64f
        targetY = 0.18f + random.nextFloat() * 0.52f
    }

    private fun mapPoint(x: Float, y: Float): PointF? {
        if (width <= 0 || height <= 0) {
            return null
        }
        val scale = max(width / sourceWidth.toFloat(), height / sourceHeight.toFloat())
        val drawnWidth = sourceWidth * scale
        val drawnHeight = sourceHeight * scale
        val offsetX = (width - drawnWidth) / 2f
        val offsetY = (height - drawnHeight) / 2f
        return PointF(offsetX + x * drawnWidth, offsetY + y * drawnHeight)
    }

    private enum class CursorMode {
        DRAW,
        HOVER,
        SWATCH,
        CLEAR
    }

    enum class PlayMode {
        PAINT,
        GAME
    }

    private data class Swatch(val color: Int, val glow: Int)

    private data class FingertipPoint(val x: Float, val y: Float)

    private companion object {
        private const val MAX_TRAIL_POINTS = 9
        private const val TARGET_RADIUS = 0.085f
        private const val CAPTURE_COOLDOWN_MS = 450L
        private const val CLEAR_HOLD_MS = 600L
        private const val SWATCH_HOLD_MS = 450L
        private const val MIN_BRUSH_RADIUS = 3f
        private const val MAX_BRUSH_RADIUS = 22f
        private const val MIN_HAND_SIZE = 0.09f
        private const val MAX_HAND_SIZE = 0.26f
        private const val BRUSH_SMOOTHING = 0.18f
        private const val SPACING_PER_RADIUS = 1.3f
    }
}
