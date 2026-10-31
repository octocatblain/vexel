package com.vexel.launcher

import android.app.Activity
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import kotlin.math.abs

/**
 * A lock screen drawn over the system keyguard: big clock, weather, the dynamic island, torch and
 * camera shortcuts, and swipe up to unlock. The system keyguard still guards the phone: unlocking
 * asks it to dismiss, so a PIN, pattern or fingerprint is still required if one is set.
 */
class LockScreenActivity : Activity() {

    private lateinit var island: IslandView
    private lateinit var info: LinearLayout
    private lateinit var shortcuts: LinearLayout
    private lateinit var homeBar: View
    private var torchOn = false
    private var downY = 0f
    private var downX = 0f

    private val islandListener: (List<IslandItem>) -> Unit = { island.setItems(it) }

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.setDecorFitsSystemWindows(false)

        val root = FrameLayout(this)
        root.addView(Backdrop(this), FrameLayout.LayoutParams(MATCH, MATCH))

        info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        info.addView(TextClock(this).apply {
            format12Hour = "EEEE, d MMMM"
            format24Hour = "EEEE, d MMMM"
            setTextColor(Color.WHITE)
            textSize = 20f
            setShadowLayer(8f, 0f, 1f, Color.argb(120, 0, 0, 0))
        })
        info.addView(TextClock(this).apply {
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
            setTextColor(Color.WHITE)
            textSize = 104f
            typeface = Fonts.medium(context)
            includeFontPadding = false
            setShadowLayer(12f, 0f, 2f, Color.argb(120, 0, 0, 0))
        })
        Weather.cached(this)?.let { r ->
            val (glyph, text) = Weather.describe(r.code)
            info.addView(TextView(this).apply {
                setTextColor(Color.WHITE)
                textSize = 16f
                typeface = Fonts.bold(context)
                background = glass(dp(22).toFloat())
                setPadding(dp(16), dp(8), dp(16), dp(8))
                compoundDrawablePadding = dp(8)
                setCompoundDrawablesRelativeWithIntrinsicBounds(
                    BitmapDrawable(resources, Glyphs.tinted(glyph, dp(22), Color.WHITE)), null, null, null
                )
                this.text = "${r.tempC}°  $text"
            }, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(12) })
        }
        root.addView(info, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP))

        shortcuts = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        shortcuts.addView(roundButton("flashlight") { toggleTorch(it) })
        shortcuts.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        shortcuts.addView(roundButton("camera") { openCamera() })
        root.addView(shortcuts, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM).apply {
            marginStart = dp(40)
            marginEnd = dp(40)
        })

        homeBar = View(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.argb(210, 255, 255, 255))
                cornerRadius = dp(3).toFloat()
            }
        }
        root.addView(homeBar, FrameLayout.LayoutParams(dp(140), dp(5), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))

        island = IslandView(this)
        root.addView(island, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP))

        setContentView(root)
        root.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            (info.layoutParams as FrameLayout.LayoutParams).topMargin = bars.top + dp(88)
            (island.layoutParams as FrameLayout.LayoutParams).topMargin = bars.top + dp(6)
            (shortcuts.layoutParams as FrameLayout.LayoutParams).bottomMargin = bars.bottom + dp(48)
            (homeBar.layoutParams as FrameLayout.LayoutParams).bottomMargin = bars.bottom + dp(8)
            root.requestLayout()
            insets
        }
        root.requestApplyInsets()

        IslandRepository.listen(islandListener)
        island.setItems(IslandRepository.items)

        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenOff, filter, RECEIVER_NOT_EXPORTED)
        else registerReceiver(screenOff, filter)
    }

    override fun onDestroy() {
        IslandRepository.unlisten(islandListener)
        unregisterReceiver(screenOff)
        if (torchOn) setTorch(false)
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Back does nothing on a lock screen.
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
            }
            MotionEvent.ACTION_UP -> {
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (dy < -dp(120) && abs(dy) > abs(dx) * 1.5f) unlock()
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun unlock() {
        getSystemService(KeyguardManager::class.java).requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = finish()
            }
        )
    }

    private fun openCamera() {
        try {
            startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: RuntimeException) {
            // No camera app answers the secure-camera intent.
        }
    }

    private fun toggleTorch(button: ImageView) {
        setTorch(!torchOn)
        button.alpha = if (torchOn) 1f else 0.7f
    }

    private fun setTorch(on: Boolean) {
        val cm = getSystemService(CameraManager::class.java)
        try {
            val id = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return
            cm.setTorchMode(id, on)
            torchOn = on
        } catch (e: CameraAccessException) {
            // The camera is busy; leave the torch as it was.
        }
    }

    /** 56dp glass circle holding a glyph, like the iPhone's torch and camera buttons. */
    private fun roundButton(glyph: String, onClick: (ImageView) -> Unit) = ImageView(this).apply {
        val size = dp(56)
        layoutParams = LinearLayout.LayoutParams(size, size)
        background = glass(size / 2f)
        setImageBitmap(Glyphs.tinted(glyph, size, Color.WHITE, 0.46f))
        alpha = 0.7f
        setOnClickListener { onClick(this) }
    }

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
