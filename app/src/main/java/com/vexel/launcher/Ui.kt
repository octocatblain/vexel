package com.vexel.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable

/** The launcher's design rules, in one place. See DESIGN.md. */
object Design {
    /** Icon grid: every glyph is drawn on 24 x 24 units. */
    const val GRID = 24f

    /** One stroke weight for every glyph, in grid units. */
    const val STROKE = 2f

    /** App tile corner radius as a fraction of tile size. */
    const val TILE_RADIUS = 0.2f

    /** Share of the tile the glyph's 24-unit box occupies (safe area). */
    const val GLYPH_BOX = 0.52f
}

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

/** Frosted-glass look: translucent white gradient with a light edge. */
fun glass(radius: Float, strokePx: Int = 2): GradientDrawable = GradientDrawable(
    GradientDrawable.Orientation.TOP_BOTTOM,
    intArrayOf(Color.argb(95, 255, 255, 255), Color.argb(40, 255, 255, 255))
).apply {
    cornerRadius = radius
    setStroke(strokePx, Color.argb(120, 255, 255, 255))
}

/** Floating frosted capsule for the favourites: milky body, bright rim and an inner top sheen. */
fun glassDock(radius: Float): Drawable {
    val body = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Color.argb(34, 255, 255, 255), Color.argb(12, 230, 236, 250))
    ).apply {
        cornerRadius = radius
        setStroke(2, Color.argb(110, 255, 255, 255))
    }
    val sheen = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Color.argb(28, 255, 255, 255), Color.argb(0, 255, 255, 255))
    ).apply { cornerRadius = radius }
    return LayerDrawable(arrayOf(body, sheen)).apply {
        setLayerInset(1, 6, 6, 6, 60)
    }
}

/** Purple-black backdrop: deep violet gradient with two soft glows that drift as pages change. */
class Backdrop(context: Context) : View(context) {
    var progress = 0f
        set(v) {
            field = v
            invalidate()
        }

    private val base = Paint()
    private val glow = Paint()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        base.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(Color.rgb(10, 5, 18), Color.rgb(22, 10, 43), Color.rgb(6, 3, 11)),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, base)
        drawGlow(canvas, w * (0.15f - 0.2f * progress), h * 0.2f, w * 1.1f, Color.rgb(124, 58, 237), 105)
        drawGlow(canvas, w * (0.9f - 0.3f * progress), h * 0.88f, w * 0.95f, Color.rgb(88, 28, 135), 95)
    }

    private fun drawGlow(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, alpha: Int) {
        glow.shader = RadialGradient(
            cx, cy, r,
            Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)),
            Color.argb(0, Color.red(color), Color.green(color), Color.blue(color)),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), glow)
    }
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
