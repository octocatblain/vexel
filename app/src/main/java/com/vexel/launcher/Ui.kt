package com.vexel.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
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
    const val TILE_RADIUS = 0.28f

    /** Share of the tile the glyph's 24-unit box occupies (safe area). */
    const val GLYPH_BOX = 0.52f
}

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

/** Purple-tinted glass: translucent violet gradient with a light lavender edge. */
fun glass(radius: Float, strokePx: Int = 2): GradientDrawable = GradientDrawable(
    GradientDrawable.Orientation.TOP_BOTTOM,
    intArrayOf(Color.argb(96, 168, 120, 255), Color.argb(44, 100, 52, 196))
).apply {
    cornerRadius = radius
    setStroke(strokePx, Color.argb(130, 206, 178, 255))
}

/** Floating glass capsule for the favourites: violet-tinted body, bright rim and an inner top sheen. */
fun glassDock(radius: Float): Drawable {
    val body = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Color.argb(70, 156, 108, 250), Color.argb(34, 88, 44, 176))
    ).apply {
        cornerRadius = radius
        setStroke(2, Color.argb(130, 206, 178, 255))
    }
    val sheen = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Color.argb(40, 255, 255, 255), Color.argb(0, 255, 255, 255))
    ).apply { cornerRadius = radius }
    return LayerDrawable(arrayOf(body, sheen)).apply {
        setLayerInset(1, 6, 6, 6, 60)
    }
}

/**
 * Black and royal-purple backdrop in slow motion: three soft purple glows drift on their own
 * orbits over black, so the page always feels alive. Animates only while [running].
 */
class Backdrop(context: Context) : View(context) {

    /** Pauses the animation when the screen isn't visible. */
    var running = true
        set(v) {
            field = v
            if (v) postInvalidateOnAnimation()
        }

    private class Glow(
        val color: Int, val alpha: Int, val radius: Float,
        val cx: Float, val cy: Float, val ax: Float, val ay: Float,
        val periodX: Float, val periodY: Float, val phase: Float
    )

    // Royal purple family; positions and sizes are fractions of the view.
    private val glows = listOf(
        Glow(Color.rgb(91, 33, 182), 150, 1.00f, 0.25f, 0.25f, 0.30f, 0.16f, 23f, 31f, 0f),
        Glow(Color.rgb(124, 58, 237), 110, 0.80f, 0.80f, 0.60f, 0.28f, 0.22f, 29f, 19f, 2.1f),
        Glow(Color.rgb(67, 20, 140), 140, 1.10f, 0.45f, 0.95f, 0.35f, 0.10f, 37f, 27f, 4.2f)
    )

    private val base = Paint()
    private val paint = Paint()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        base.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(Color.rgb(4, 2, 9), Color.rgb(10, 5, 20), Color.rgb(3, 1, 7)),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, base)
        val t = SystemClock.uptimeMillis() / 1000f
        val twoPi = (2 * Math.PI).toFloat()
        for (g in glows) {
            val x = w * (g.cx + g.ax * sin(twoPi * t / g.periodX + g.phase))
            val y = h * (g.cy + g.ay * cos(twoPi * t / g.periodY + g.phase))
            val breathe = 1f + 0.08f * sin(twoPi * t / (g.periodX + 11f) + g.phase)
            val c = g.color
            paint.shader = RadialGradient(
                x, y, w * g.radius * breathe,
                Color.argb(g.alpha, Color.red(c), Color.green(c), Color.blue(c)),
                Color.argb(0, Color.red(c), Color.green(c), Color.blue(c)),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, w, h, paint)
        }
        if (running && isAttachedToWindow) postInvalidateOnAnimation()
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
