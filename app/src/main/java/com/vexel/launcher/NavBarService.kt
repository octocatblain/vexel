package com.vexel.launcher

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * A Samsung-style navigation bar (recents, home, back) drawn over the system buttons in every app.
 * It only presses the three system buttons through global actions; it never reads what is on screen.
 */
class NavBarService : AccessibilityService() {

    companion object {
        /** True while the system has this service bound; the launcher uses it to notice when the bar was lost. */
        @Volatile
        var running = false
    }

    private var bar: View? = null

    private val screenState = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        running = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenState, filter, RECEIVER_NOT_EXPORTED)
        else registerReceiver(screenState, filter)
        refresh()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        running = false
        remove()
        runCatching { unregisterReceiver(screenState) }
        return super.onUnbind(intent)
    }

    /** The bar stays off the lock screen, where the system keyguard owns the touches. */
    private fun refresh() {
        if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) remove() else show()
    }

    private fun show() {
        if (bar != null) return
        val view = NavRow(this) { action -> performGlobalAction(action) }
        val lp = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            barHeight(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE
        ).apply { gravity = Gravity.BOTTOM }
        getSystemService(WindowManager::class.java).addView(view, lp)
        bar = view
    }

    private fun remove() {
        bar?.let { runCatching { getSystemService(WindowManager::class.java).removeView(it) } }
        bar = null
    }

    /** Same height as the system bar, so the buttons sit exactly where the old ones were. */
    private fun barHeight(): Int {
        val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else (48 * resources.displayMetrics.density).toInt()
    }

    /**
     * The whole bar in one view, so the spacing can be exact: recents, home and back are laid out with
     * four equal gaps — screen edge to recents, recents to home, home to back, back to screen edge —
     * measured between the drawn shapes, not between invisible cells. A tap counts for the third of the
     * bar it lands in, so every button keeps a wide touch target.
     */
    private class NavRow(
        context: Context,
        private val perform: (Int) -> Boolean
    ) : View(context) {

        private val d = resources.displayMetrics.density
        private var pressed = -1

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(235, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        // Drawn widths, stroke included.
        private val recentsW = 14.4f * d // three bars, 6dp apart, 2.4dp stroke
        private val homeW = 21f * d // rounded square, 2dp stroke
        private val backW = 10.9f * d // chevron, 2.4dp stroke

        init {
            setBackgroundColor(Color.BLACK)
        }

        override fun onDraw(canvas: Canvas) {
            val gap = (width - recentsW - homeW - backW) / 4f
            val cy = height / 2f
            var x = gap

            paint.alpha = alphaFor(0)
            paint.strokeWidth = 2.4f * d
            for (i in 0..2) {
                val bx = x + 1.2f * d + i * 6f * d
                canvas.drawLine(bx, cy - 8f * d, bx, cy + 8f * d, paint)
            }
            x += recentsW + gap

            paint.alpha = alphaFor(1)
            paint.strokeWidth = 2f * d
            canvas.drawRoundRect(RectF(x + d, cy - 8.5f * d, x + homeW - d, cy + 8.5f * d), 4.5f * d, 4.5f * d, paint)
            x += homeW + gap

            paint.alpha = alphaFor(2)
            paint.strokeWidth = 2.4f * d
            canvas.drawPath(
                Path().apply {
                    moveTo(x + backW - 1.2f * d, cy - 8.5f * d)
                    lineTo(x + 1.2f * d, cy)
                    lineTo(x + backW - 1.2f * d, cy + 8.5f * d)
                },
                paint
            )
        }

        private fun alphaFor(i: Int) = if (pressed == i) 115 else 235

        private fun zone(e: MotionEvent) = (e.x / (width / 3f)).toInt().coerceIn(0, 2)

        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pressed = zone(e)
                    invalidate()
                }
                MotionEvent.ACTION_UP -> {
                    val hit = zone(e)
                    if (hit == pressed) {
                        perform(
                            when (hit) {
                                0 -> AccessibilityService.GLOBAL_ACTION_RECENTS
                                1 -> AccessibilityService.GLOBAL_ACTION_HOME
                                else -> AccessibilityService.GLOBAL_ACTION_BACK
                            }
                        )
                    }
                    pressed = -1
                    invalidate()
                }
                MotionEvent.ACTION_CANCEL -> {
                    pressed = -1
                    invalidate()
                }
            }
            return true
        }
    }
}
