package eu.pretix.libpretixui.android.views

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import eu.pretix.libpretixui.android.R

/**
 * Based on com.github.apg-mobile:android-round-textview
 * https://github.com/apg-mobile/android-round-textview/blob/master/roundtextview/src/main/java/com/apg/mobile/roundtextview/RoundTextView.java
 *
 * Apache License, Version 2.0
 *
 * Created by X-tivity on 3/29/2017 AD.
 */

class RoundTextView : AppCompatTextView {
    companion object {
        private const val DEFAULT_COLOR = Color.TRANSPARENT
        private const val DEFAULT_RADIUS = 5f
    }

    private var tvBgColor = DEFAULT_COLOR
    private var tvRadius = DEFAULT_RADIUS

    constructor(context: Context) : super(context) {
        setViewBackground()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        extractAttribute(context, attrs)
        setViewBackground()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr,
    ) {
        extractAttribute(context, attrs)
        setViewBackground()
    }

    private fun extractAttribute(
        context: Context,
        attrs: AttributeSet?,
    ) {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.RoundTextView, 0, 0)
        try {
            tvBgColor = ta.getColor(R.styleable.RoundTextView_bgColor, DEFAULT_COLOR)
            tvRadius = ta.getDimension(R.styleable.RoundTextView_radius, DEFAULT_RADIUS)
        } finally {
            ta.recycle()
        }
    }

    fun setRadius(radius: Int) {
        tvRadius = radius.toFloat()
        setViewBackground()
    }

    fun setBgColor(color: Int) {
        tvBgColor = color
        setViewBackground()
    }

    private fun setViewBackground() {
        val outerR = FloatArray(8, { tvRadius })
        val drawable = ShapeDrawable()
        drawable.setShape(RoundRectShape(outerR, null, null))
        drawable.paint.setColor(tvBgColor)

        background = drawable
    }
}
