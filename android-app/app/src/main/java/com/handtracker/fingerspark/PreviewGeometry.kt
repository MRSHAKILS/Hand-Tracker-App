package com.handtracker.fingerspark

import kotlin.math.max
import kotlin.math.hypot

internal data class PreviewGeometry(
    val viewWidth: Float,
    val viewHeight: Float,
    val sourceWidth: Int,
    val sourceHeight: Int
) {
    init {
        require(viewWidth > 0 && viewHeight > 0 && sourceWidth > 0 && sourceHeight > 0)
    }

    private val scale = max(viewWidth / sourceWidth, viewHeight / sourceHeight)
    val drawnWidth = sourceWidth * scale
    val drawnHeight = sourceHeight * scale
    val offsetX = (viewWidth - drawnWidth) / 2f
    val offsetY = (viewHeight - drawnHeight) / 2f

    fun screenX(normalizedX: Float) = offsetX + normalizedX * drawnWidth
    fun screenY(normalizedY: Float) = offsetY + normalizedY * drawnHeight

    fun isTargetHit(x: Float, y: Float, targetX: Float, targetY: Float, radius: Float): Boolean {
        if (!x.isFinite() || !y.isFinite() || radius < 0f) return false
        val screenX = screenX(x)
        val screenY = screenY(y)
        if (screenX !in 0f..viewWidth || screenY !in 0f..viewHeight) return false
        return hypot((x - targetX) * drawnWidth, (y - targetY) * drawnHeight) <= radius
    }

    fun targetX(fraction: Float) = (viewWidth * (0.18f + fraction.coerceIn(0f, 1f) * 0.64f) - offsetX) / drawnWidth
    fun targetY(fraction: Float) = (viewHeight * (0.18f + fraction.coerceIn(0f, 1f) * 0.52f) - offsetY) / drawnHeight
}
