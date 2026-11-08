package com.handtracker.fingerspark

internal data class HomeLayoutSpec(
    val sidePadding: Int,
    val stackModes: Boolean,
    val artworkHeight: Int,
    val toolbarWidth: Int
)

internal object HomeLayoutPolicy {
    fun forWindow(widthDp: Int, heightDp: Int, fontScale: Float): HomeLayoutSpec {
        val width = widthDp.coerceAtLeast(1)
        val sidePadding = maxOf(24, (width - 560) / 2)
        val contentWidth = width - sidePadding * 2
        return HomeLayoutSpec(
            sidePadding = sidePadding,
            stackModes = contentWidth < 288 || fontScale > 1.25f,
            artworkHeight = if (heightDp < 640) 140 else 176,
            toolbarWidth = (width - 32).coerceIn(1, 352)
        )
    }
}
