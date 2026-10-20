package com.handtracker.fingerspark

import android.content.Context
import android.util.AttributeSet
import android.view.View

class SparkBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    init {
        setBackgroundColor(SparkStyle.surface)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
}
