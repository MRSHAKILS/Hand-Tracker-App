package com.handtracker.fingerspark

import kotlin.math.max

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

    fun targetX(fraction: Float) = (viewWidth * (0.18f + fraction.coerceIn(0f, 1f) * 0.64f) - offsetX) / drawnWidth
    fun targetY(fraction: Float) = (viewHeight * (0.18f + fraction.coerceIn(0f, 1f) * 0.52f) - offsetY) / drawnHeight
}
