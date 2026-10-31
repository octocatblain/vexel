package com.vexel.launcher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.PendingIntent
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.view.Gravity
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * An iPhone-style Dynamic Island: a black pill that shows the top live activity
 * (music, alarm, call, ride, timer) and expands on tap to show controls.
 */
class IslandView(context: Context) : FrameLayout(context) {

    var onSetupClick: (() -> Unit)? = null

    private var items: List<IslandItem> = emptyList()
    private var currentKey: String? = null
    private var expanded = false
    private var radius = 0f
    private var anim: ValueAnimator? = null
    private val main = Handler(Looper.getMainLooper())

    private val bg = GradientDrawable().apply { setColor(Color.BLACK) }
    private val pill = FrameLayout(context)
    private val compact = LinearLayout(context)
    private val full = LinearLayout(context)

    private var progress: ProgressView? = null
    private var elapsedLabel: TextView? = null
    private var durationLabel: TextView? = null

    private fun d(v: Int) = context.dp(v)
    private val compactH = d(38)
    private fun compactW() = d(if (current()?.kind == IslandKind.MEDIA) 156 else 200)
    private val compactRadius = compactH / 2f
    private val expandedRadius = d(34).toFloat()

    private val collapseTask = Runnable { collapse() }
    private val ticker = object : Runnable {
        override fun run() {
            updateProgress()
            if (expanded) main.postDelayed(this, 500)
        }
    }

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true.also { armCollapse() }
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (expanded) collapse() else expand()
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            if (items.size > 1 && abs(vx) > abs(vy) && abs(vx) > 600) {
                val i = items.indexOfFirst { it.key == currentKey }.coerceAtLeast(0)
                val next = if (vx < 0) (i + 1) % items.size else (i - 1 + items.size) % items.size
                currentKey = items[next].key
                render()
                return true
            }
            return false
        }
    })

    init {
        radius = compactRadius
        bg.cornerRadius = radius
        pill.apply {
            background = bg
            elevation = d(8).toFloat()
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, radius)
                }
            }
            clipToOutline = true
            visibility = GONE
            alpha = 0f
            scaleX = 0.6f
            scaleY = 0.6f
            setOnTouchListener { _, e -> gestures.onTouchEvent(e) }
        }
        compact.orientation = LinearLayout.HORIZONTAL
        compact.gravity = Gravity.CENTER_VERTICAL
        full.orientation = LinearLayout.VERTICAL
        full.setPadding(d(24), d(16), d(24), d(8))
        full.visibility = GONE
        full.alpha = 0f
        pill.addView(compact, LayoutParams(LayoutParams.MATCH_PARENT, compactH, Gravity.TOP))
        pill.addView(full, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP))
        addView(pill, LayoutParams(compactW(), compactH, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
    }

    // ---- state ----------------------------------------------------------------------------

    fun setItems(newItems: List<IslandItem>) {
        val oldKeys = items.map { it.key }.toSet()
        items = newItems
        if (items.isEmpty()) {
            currentKey = null
            collapseNow()
            hide()
            return
        }
        val fresh = items.firstOrNull { it.key !in oldKeys && (it.kind == IslandKind.ALARM || it.kind == IslandKind.CALL) }
        currentKey = fresh?.key
            ?: currentKey?.takeIf { k -> items.any { it.key == k } }
            ?: items.first().key
        render()
        show()
        if (fresh != null) expand(sticky = true)
    }

    private fun current(): IslandItem? = items.firstOrNull { it.key == currentKey } ?: items.firstOrNull()

    private fun show() {
        if (pill.visibility == VISIBLE && pill.alpha == 1f) return
        pill.visibility = VISIBLE
        pill.pivotX = pill.layoutParams.width / 2f
        pill.pivotY = 0f
        pill.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setInterpolator(OvershootInterpolator(1.2f)).setDuration(320).start()
    }

    private fun hide() {
        pill.animate().alpha(0f).scaleX(0.6f).scaleY(0.6f).setDuration(200)
            .setInterpolator(null)
            .withEndAction { if (items.isEmpty()) pill.visibility = GONE }
            .start()
    }

    private fun collapseNow() {
        anim?.cancel()
        expanded = false
        main.removeCallbacks(collapseTask)
        main.removeCallbacks(ticker)
        radius = compactRadius
        bg.cornerRadius = radius
        full.visibility = GONE
        full.alpha = 0f
        compact.alpha = 1f
        setPillSize(compactW(), compactH)
    }

    private fun setPillSize(w: Int, h: Int) {
        val lp = pill.layoutParams
        lp.width = w
        lp.height = h
        pill.layoutParams = lp
    }

    // ---- expand / collapse ----------------------------------------------------------------

    private fun expand(sticky: Boolean = false) {
        if (expanded) return
        expanded = true
        val w = (width - d(24)).coerceAtLeast(d(260))
        full.layoutParams = LayoutParams(w, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        full.measure(
            MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        animateSize(w, full.measuredHeight, expandedRadius, true)
        main.removeCallbacks(collapseTask)
        val kind = current()?.kind
        if (!sticky && kind != IslandKind.ALARM && kind != IslandKind.CALL) armCollapse()
        main.removeCallbacks(ticker)
        main.post(ticker)
    }

    private fun collapse() {
        if (!expanded) return
        expanded = false
        main.removeCallbacks(collapseTask)
        main.removeCallbacks(ticker)
        animateSize(compactW(), compactH, compactRadius, false)
    }

    private fun armCollapse() {
        main.removeCallbacks(collapseTask)
        val kind = current()?.kind
        if (expanded && kind != IslandKind.ALARM && kind != IslandKind.CALL) main.postDelayed(collapseTask, 8000)
    }

    private fun animateSize(toW: Int, toH: Int, toR: Float, toExpanded: Boolean) {
        anim?.cancel()
        if (toExpanded) full.visibility = VISIBLE
        val lp = pill.layoutParams
        val w0 = lp.width
        val h0 = lp.height
        val r0 = radius
        var cancelled = false
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 340
            interpolator = OvershootInterpolator(0.7f)
            addUpdateListener {
                val t = it.animatedValue as Float
                lp.width = (w0 + (toW - w0) * t).toInt()
                lp.height = (h0 + (toH - h0) * t).toInt()
                radius = r0 + (toR - r0) * t.coerceIn(0f, 1f)
                bg.cornerRadius = radius
                pill.invalidateOutline()
                pill.layoutParams = lp
                val progressToExpanded = (if (toExpanded) t else 1f - t).coerceIn(0f, 1f)
                compact.alpha = 1f - (progressToExpanded * 3f).coerceAtMost(1f)
                full.alpha = ((progressToExpanded - 0.3f) / 0.7f).coerceIn(0f, 1f)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(a: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(a: Animator) {
                    if (cancelled) return
                    if (!toExpanded) full.visibility = GONE
                }
            })
            start()
        }
    }

    // ---- rendering ------------------------------------------------------------------------

    private fun render() {
        val item = current() ?: return
        renderCompact(item)
        renderFull(item)
        if (!expanded) {
            setPillSize(compactW(), compactH)
        } else {
            val w = pill.layoutParams.width
            full.measure(
                MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            )
            setPillSize(w, full.measuredHeight)
        }
    }

    private fun renderCompact(item: IslandItem) {
        compact.removeAllViews()
        if (item.kind != IslandKind.SETUP) {
            compact.addView(iconView(item, d(26), d(8)), LinearLayout.LayoutParams(d(26), d(26)).apply {
                marginStart = d(10)
            })
        }
        if (item.kind == IslandKind.MEDIA) {
            compact.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            compact.addView(EqView(context).also { it.active = item.playing },
                LinearLayout.LayoutParams(d(22), d(16)).apply { marginEnd = d(14) })
        } else {
            compact.addView(label(item.title, 13f, Color.WHITE, bold = true).apply {
                gravity = Gravity.CENTER
                setPadding(d(10), 0, d(14), 0)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun renderFull(item: IslandItem) {
        full.removeAllViews()
        progress = null
        elapsedLabel = null
        durationLabel = null
        when (item.kind) {
            IslandKind.MEDIA -> renderMedia(item)
            IslandKind.SETUP -> renderSetup(item)
            else -> renderGeneric(item)
        }
        if (items.size > 1) full.addView(dots(), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = d(10) })
    }

    private fun renderMedia(item: IslandItem) {
        val c = item.controller ?: return
        val top = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(iconView(item, d(56), d(11)).apply {
            setOnClickListener { openApp(item.pkg); collapse() }
        }, LinearLayout.LayoutParams(d(56), d(56)))
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        col.addView(label(item.title, 17f, Color.WHITE, bold = true))
        col.addView(label(item.subtitle, 14f, Color.argb(170, 255, 255, 255)), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = d(2) })
        top.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = d(16)
        })
        top.addView(EqView(context).also { it.active = item.playing }, LinearLayout.LayoutParams(d(24), d(18)))
        full.addView(top)

        if (item.duration > 0) {
            val bar = ProgressView(context).also { progress = it }
            full.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, d(4)).apply {
                topMargin = d(16)
            })
            val times = LinearLayout(context)
            elapsedLabel = label("0:00", 11f, Color.argb(160, 255, 255, 255))
            durationLabel = label(fmt(item.duration), 11f, Color.argb(160, 255, 255, 255)).apply {
                gravity = Gravity.END
            }
            times.addView(elapsedLabel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            times.addView(durationLabel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            full.addView(times, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = d(4) })
        }

        // Transport controls sit on the 8dp rhythm: 56dp targets, 24dp glyphs, 16dp gaps.
        val controls = LinearLayout(context).apply { gravity = Gravity.CENTER }
        val target = d(56)
        val gap = d(16)
        controls.addView(
            control("prev") { c.transportControls.skipToPrevious() },
            LinearLayout.LayoutParams(target, target)
        )
        controls.addView(
            control(if (item.playing) "pause" else "play") {
                if (item.playing) c.transportControls.pause() else c.transportControls.play()
            },
            LinearLayout.LayoutParams(target, target).apply { marginStart = gap; marginEnd = gap }
        )
        controls.addView(
            control("next") { c.transportControls.skipToNext() },
            LinearLayout.LayoutParams(target, target)
        )
        full.addView(controls, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = d(4) })
        updateProgress()
    }

    private fun control(glyph: String, onClick: () -> Unit) = ImageView(context).apply {
        setImageBitmap(Glyphs.tinted(glyph, d(56), Color.WHITE, 0.46f))
        isClickable = true
        setOnClickListener {
            onClick()
            armCollapse()
        }
    }

    private fun renderGeneric(item: IslandItem) {
        val top = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(iconView(item, d(44), d(11)), LinearLayout.LayoutParams(d(44), d(44)))
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        col.addView(label(item.title, 16f, Color.WHITE, bold = true, lines = 2))
        if (item.subtitle.isNotEmpty()) col.addView(label(item.subtitle, 13f, Color.argb(170, 255, 255, 255), lines = 3))
        top.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = d(12)
        })
        top.setOnClickListener {
            send(item.tap)
            collapse()
        }
        full.addView(top)
        if (item.actions.isNotEmpty()) {
            val row = LinearLayout(context)
            item.actions.take(3).forEachIndexed { i, action ->
                row.addView(pillButton(action.title.toString()) {
                    send(action.actionIntent)
                    collapse()
                }, LinearLayout.LayoutParams(0, d(40), 1f).apply { if (i > 0) marginStart = d(8) })
            }
            full.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = d(14) })
        }
    }

    private fun renderSetup(item: IslandItem) {
        full.addView(label("Dynamic Island", 16f, Color.WHITE, bold = true))
        full.addView(label(
            "Allow notification access so music, alarms, calls and rides can show here.",
            13f, Color.argb(170, 255, 255, 255), lines = 3
        ), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = d(4) })
        full.addView(pillButton("Open settings") { onSetupClick?.invoke(); collapse() },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, d(40)).apply { topMargin = d(14) })
    }

    // ---- helpers --------------------------------------------------------------------------

    private fun updateProgress() {
        val item = current() ?: return
        val bar = progress ?: return
        if (item.kind != IslandKind.MEDIA || item.duration <= 0) return
        var pos = item.position
        if (item.playing) pos += ((SystemClock.elapsedRealtime() - item.positionTime) * item.speed).toLong()
        pos = pos.coerceIn(0, item.duration)
        bar.fraction = pos.toFloat() / item.duration
        elapsedLabel?.text = fmt(pos)
    }

    private fun fmt(ms: Long): String {
        val s = ms / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }

    private fun send(pi: PendingIntent?) {
        try {
            pi?.send()
        } catch (e: PendingIntent.CanceledException) {
        }
    }

    private fun openApp(pkg: String) {
        context.packageManager.getLaunchIntentForPackage(pkg)?.let { context.startActivity(it) }
    }

    private fun label(text: String, sp: Float, color: Int, bold: Boolean = false, lines: Int = 1) =
        TextView(context).apply {
            this.text = text
            textSize = sp
            setTextColor(color)
            maxLines = lines
            ellipsize = TextUtils.TruncateAt.END
            typeface = if (bold) Fonts.bold(context) else Fonts.regular(context)
        }

    private fun pillButton(text: String, onClick: () -> Unit) = TextView(context).apply {
        this.text = text
        textSize = 14f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        typeface = Fonts.bold(context)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        background = GradientDrawable().apply {
            setColor(Color.argb(55, 255, 255, 255))
            cornerRadius = d(20).toFloat()
        }
        setOnClickListener { onClick() }
    }

    private fun dots() = LinearLayout(context).apply {
        items.forEach {
            val dot = View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (it.key == currentKey) Color.WHITE else Color.argb(80, 255, 255, 255))
                }
            }
            addView(dot, LinearLayout.LayoutParams(d(6), d(6)).apply { marginStart = d(3); marginEnd = d(3) })
        }
    }

    private fun iconView(item: IslandItem, size: Int, corner: Int): ImageView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        val art = item.art
        if (art != null) {
            setImageBitmap(art)
        } else {
            try {
                setImageDrawable(context.packageManager.getApplicationIcon(item.pkg))
            } catch (e: Exception) {
            }
        }
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, corner.toFloat())
            }
        }
        clipToOutline = true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        main.removeCallbacksAndMessages(null)
    }

    // ---- small custom views ---------------------------------------------------------------

    private class ProgressView(context: Context) : View(context) {
        var fraction = 0f
            set(v) {
                field = v
                invalidate()
            }
        private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 255, 255, 255) }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

        override fun onDraw(canvas: Canvas) {
            val r = height / 2f
            canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, track)
            canvas.drawRoundRect(0f, 0f, width * fraction.coerceIn(0f, 1f), height.toFloat(), r, r, fill)
        }
    }

    private class EqView(context: Context) : View(context) {
        var active = true
            set(v) {
                field = v
                updateAnimator()
            }
        private var phase = 0f
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private val animator = ValueAnimator.ofFloat(0f, (2 * PI).toFloat()).apply {
            duration = 900
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
        }

        private fun updateAnimator() {
            if (active && isAttachedToWindow) {
                if (!animator.isRunning) animator.start()
            } else {
                animator.cancel()
                invalidate()
            }
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            updateAnimator()
        }

        override fun onDetachedFromWindow() {
            animator.cancel()
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            val bars = 4
            val gap = width * 0.12f
            val bw = (width - gap * (bars - 1)) / bars
            for (i in 0 until bars) {
                val level = if (active) 0.35f + 0.65f * (0.5f + 0.5f * sin(phase + i * 1.5f)) else 0.25f
                val h = height * level
                val x = i * (bw + gap)
                canvas.drawRoundRect(x, height - h, x + bw, height.toFloat(), bw / 2, bw / 2, paint)
            }
        }
    }
}
