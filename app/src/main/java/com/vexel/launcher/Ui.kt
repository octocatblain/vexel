package com.vexel.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

/** Frosted-glass look: translucent white gradient with a light edge. */
fun glass(radius: Float, strokePx: Int = 2): GradientDrawable = GradientDrawable(
    GradientDrawable.Orientation.TOP_BOTTOM,
    intArrayOf(Color.argb(95, 255, 255, 255), Color.argb(40, 255, 255, 255))
).apply {
    cornerRadius = radius
    setStroke(strokePx, Color.argb(120, 255, 255, 255))
}

fun Drawable.toBitmap(): Bitmap {
    if (this is BitmapDrawable && bitmap != null) return bitmap
    val w = intrinsicWidth.coerceAtLeast(1)
    val h = intrinsicHeight.coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    setBounds(0, 0, w, h)
    draw(canvas)
    return bmp
}
