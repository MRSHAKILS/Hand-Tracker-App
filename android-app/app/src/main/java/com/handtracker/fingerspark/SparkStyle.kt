package com.handtracker.fingerspark

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable

internal object SparkStyle {
    val ink = Color.rgb(31, 44, 49)
    val secondary = Color.rgb(83, 102, 105)
    val surface = Color.rgb(248, 250, 249)
    val mint = Color.rgb(220, 242, 231)
    val coral = Color.rgb(255, 229, 219)
    val paintAccent = Color.rgb(28, 112, 88)
    val gameAccent = Color.rgb(177, 70, 51)
    val rule = Color.rgb(219, 228, 223)
    val toolbar = Color.rgb(31, 44, 49)

    fun surface(color: Int, radius: Float): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

    fun touchSurface(color: Int, radius: Float, lightRipple: Boolean = false): RippleDrawable = RippleDrawable(
        ColorStateList.valueOf(if (lightRipple) Color.argb(48, 255, 255, 255) else Color.argb(32, 28, 36, 38)),
        surface(color, radius),
        surface(Color.WHITE, radius)
    )
}
