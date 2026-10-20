package com.handtracker.fingerspark

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable

internal object SparkStyle {
    val ink = Color.rgb(28, 36, 38)
    val secondary = Color.rgb(81, 96, 97)
    val surface = Color.rgb(249, 251, 250)
    val mint = Color.rgb(183, 239, 214)
    val coral = Color.rgb(255, 195, 184)

    fun surface(color: Int, radius: Float): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

    fun touchSurface(color: Int, radius: Float): RippleDrawable = RippleDrawable(
        ColorStateList.valueOf(Color.argb(32, 28, 36, 38)),
        surface(color, radius),
        surface(Color.WHITE, radius)
    )
}
