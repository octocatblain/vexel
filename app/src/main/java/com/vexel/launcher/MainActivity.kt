package com.vexel.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import kotlin.math.abs

private data class AppEntry(
    val label: String,
    val packageName: String,
    val intent: Intent,
    val info: ResolveInfo
)

/** One page: clock, date and weather on top, every app in a four-column grid below, favourites docked at the bottom. */
class MainActivity : Activity() {

    companion object {
        private const val COLS = 5
        private const val MAX_FAVORITES = 5
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    }

    private val prefs by lazy { getSharedPreferences("vexel", MODE_PRIVATE) }

    private var allApps: List<AppEntry> = emptyList()
    private var favorites: List<AppEntry> = emptyList()
    private val iconCache = HashMap<String, BitmapDrawable>()

    private var topInset = 0
    private var bottomInset = 0
    private var startedAtTop = true

    private lateinit var root: FrameLayout
    private lateinit var scroll: ScrollView
    private lateinit var content: LinearLayout
    private lateinit var header: LinearLayout
    private lateinit var grid: LinearLayout
    private lateinit var dock: LinearLayout
    private lateinit var weatherChip: TextView
    private lateinit var island: IslandView
    private lateinit var column: LinearLayout
    private lateinit var backdrop: Backdrop

    // ---- lifecycle ------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDecorFitsSystemWindows(false)
        buildUi()
        IslandRepository.listen(islandListener)
        try {
            startForegroundService(Intent(this, LockService::class.java))
        } catch (e: RuntimeException) {
            // The lock screen service can't start right now; it will on the next launch.
        }
    }

    private val islandListener: (List<IslandItem>) -> Unit = { island.setItems(islandItems(it)) }

    override fun onPause() {
        backdrop.running = false
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        backdrop.running = true
        loadApps()
        pushIsland()
        refreshWeather()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        scrollToBottom(smooth = true)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // A launcher has nowhere to go back to.
        scrollToBottom(smooth = true)
    }

    override fun onDestroy() {
        IslandRepository.unlisten(islandListener)
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        refreshWeather()
    }

    // ---- gestures -------------------------------------------------------------------------

    private var downX = 0f
    private var downY = 0f

    /** Swiping down from the header area (clock, date, weather) pulls the notification shade. */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                startedAtTop = ev.y < header.bottom
            }
            MotionEvent.ACTION_UP -> {
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (startedAtTop && dy > dp(80) && dy > abs(dx) * 1.5f) expandNotifications()
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun expandNotifications() {
        try {
            Class.forName("android.app.StatusBarManager")
                .getMethod("expandNotificationsPanel")
                .invoke(getSystemService("statusbar"))
        } catch (e: ReflectiveOperationException) {
            // The platform doesn't expose the shade to apps; the system's own pull-down still works.
        }
    }

    // ---- UI construction ------------------------------------------------------------------

    private fun buildUi() {
        root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        backdrop = Backdrop(this)
        root.addView(backdrop, FrameLayout.LayoutParams(MATCH, MATCH))

        header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), 0, dp(24), dp(16))
        }
        header.addView(TextClock(this).apply {
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
            setTextColor(Color.WHITE)
            textSize = 56f
            typeface = Fonts.thin(context)
            includeFontPadding = false
        })
        header.addView(TextClock(this).apply {
            format12Hour = "EEEE, d MMMM"
            format24Hour = "EEEE, d MMMM"
            setTextColor(Color.LTGRAY)
            textSize = 16f
            typeface = Fonts.regular(context)
        })
        weatherChip = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Fonts.bold(context)
            background = glass(dp(20).toFloat())
            setPadding(dp(14), dp(8), dp(14), dp(8))
            text = "Weather: tap to allow location"
            setOnClickListener { requestWeatherPermissionOrRefresh() }
        }
        header.addView(weatherChip, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(14) })

        grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), 0)
        }
        // The header stays put; the app list sits under it and hugs the bottom, just above the dock.
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
        }
        content.addView(grid)

        scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = true
            addView(content, FrameLayout.LayoutParams(MATCH, WRAP))
        }
        column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(header, LinearLayout.LayoutParams(MATCH, WRAP))
        column.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
        root.addView(column, FrameLayout.LayoutParams(MATCH, MATCH))

        dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = glassDock(dp(32).toFloat())
            setPadding(dp(8), dp(14), dp(8), dp(14))
        }
        root.addView(dock, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM).apply {
            marginStart = dp(16)
            marginEnd = dp(16)
        })

        island = IslandView(this).apply {
            onSetupClick = {
                prefs.edit().putBoolean("setupSeen", true).apply()
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }
        root.addView(island, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP))

        setContentView(root)
        root.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            topInset = bars.top
            bottomInset = bars.bottom
            applyInsets()
            insets
        }
        root.requestApplyInsets()
    }

    private fun applyInsets() {
        (island.layoutParams as FrameLayout.LayoutParams).topMargin = topInset + dp(6)
        (dock.layoutParams as FrameLayout.LayoutParams).bottomMargin = bottomInset + dp(16)
        header.setPadding(dp(24), topInset + dp(56), dp(24), dp(16))
        // The app list ends where the dock begins, so icons never scroll underneath it.
        (column.layoutParams as FrameLayout.LayoutParams).bottomMargin = bottomInset + dp(16) + dp(92)
        content.setPadding(0, 0, 0, dp(8))
        root.requestLayout()
    }

    private fun refreshDock() {
        dock.removeAllViews()
        favorites.forEach { app ->
            val cell = LinearLayout(this).apply { gravity = Gravity.CENTER }
            cell.addView(ImageView(this).apply {
                setImageDrawable(monoIcon(app))
                setOnClickListener { startActivity(app.intent) }
                setOnLongClickListener { showMenu(this, app); true }
            }, LinearLayout.LayoutParams(dp(56), dp(56)))
            dock.addView(cell, LinearLayout.LayoutParams(0, WRAP, 1f))
        }
    }

    private fun refreshGrid() {
        grid.removeAllViews()
        // Apps already on the dock aren't repeated in the list.
        val docked = favorites.map { it.packageName }.toSet()
        val apps = allApps.filter { it.packageName !in docked }

        // Rows fill from the end: every row is full except the top one, whose icons sit at the right.
        val short = apps.size % COLS
        val rows = ArrayList<List<AppEntry>>()
        if (short > 0) rows += apps.subList(0, short)
        apps.drop(short).chunked(COLS).forEach { rows += it }

        rows.forEach { rowApps ->
            val row = LinearLayout(this)
            repeat(COLS - rowApps.size) { row.addView(View(this), LinearLayout.LayoutParams(0, WRAP, 1f)) }
            rowApps.forEach { row.addView(appCell(it), LinearLayout.LayoutParams(0, WRAP, 1f)) }
            grid.addView(row, LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(14) })
        }
        if (!placedAtBottom) {
            placedAtBottom = true
            scrollToBottom()
        }
    }

    private var placedAtBottom = false

    private fun scrollToBottom(smooth: Boolean = false) {
        scroll.post { if (smooth) scroll.smoothScrollTo(0, Int.MAX_VALUE / 2) else scroll.scrollTo(0, Int.MAX_VALUE / 2) }
    }

    private fun appCell(app: AppEntry) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        addView(ImageView(context).apply { setImageDrawable(monoIcon(app)) }, LinearLayout.LayoutParams(dp(56), dp(56)))
        addView(TextView(context).apply {
            text = app.label
            setTextColor(Color.WHITE)
            textSize = 11f
            typeface = Fonts.regular(context)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setShadowLayer(6f, 0f, 1f, Color.argb(190, 10, 0, 30))
        }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(6) })
        setOnClickListener { startActivity(app.intent) }
        setOnLongClickListener { showMenu(this, app); true }
    }

    // ---- apps and favourites --------------------------------------------------------------

    private fun loadApps() {
        val pm = packageManager
        iconCache.clear()
        allApps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != packageName }
            .map {
                val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClassName(it.activityInfo.packageName, it.activityInfo.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName, i, it)
            }
            .sortedBy { it.label.lowercase() }
        favorites = resolveFavorites()
        refreshDock()
        refreshGrid()
    }

    private fun resolveFavorites(): List<AppEntry> {
        val saved = prefs.getString("favs", null)?.split("|")?.filter { it.isNotEmpty() }
        val names = saved ?: defaultFavorites()
        return names.mapNotNull { n -> allApps.firstOrNull { it.packageName == n } }.take(MAX_FAVORITES)
    }

    private fun defaultFavorites(): List<String> {
        val wanted = listOf("Phone", "Messages", "Spotify", "WhatsApp", "WA Business")
        return wanted.mapNotNull { w -> allApps.firstOrNull { it.label.equals(w, ignoreCase = true) }?.packageName }
    }

    private fun saveFavorites(list: List<AppEntry>) {
        prefs.edit().putString("favs", list.joinToString("|") { it.packageName }).apply()
        favorites = list
        refreshDock()
        refreshGrid()
    }

    private fun toggleFavorite(app: AppEntry) {
        when {
            favorites.any { it.packageName == app.packageName } ->
                saveFavorites(favorites.filter { it.packageName != app.packageName })
            favorites.size < MAX_FAVORITES -> saveFavorites(favorites + app)
            else -> AlertDialog.Builder(this)
                .setTitle("Replace which favourite?")
                .setItems(favorites.map { it.label }.toTypedArray()) { _, which ->
                    saveFavorites(favorites.toMutableList().also { it[which] = app })
                }
                .show()
        }
    }

    private fun showMenu(anchor: View, app: AppEntry) {
        val isFav = favorites.any { it.packageName == app.packageName }
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, if (isFav) "Remove from favourites" else "Add to favourites")
            menu.add(0, 2, 1, "App info")
            menu.add(0, 3, 2, "Uninstall")
            setOnMenuItemClickListener {
                val uri = Uri.parse("package:${app.packageName}")
                when (it.itemId) {
                    1 -> toggleFavorite(app)
                    2 -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
                    3 -> startActivity(Intent(Intent.ACTION_DELETE, uri))
                }
                true
            }
            show()
        }
    }

    // ---- weather --------------------------------------------------------------------------

    private fun showWeather(r: Weather.Reading) {
        val (glyph, text) = Weather.describe(r.code)
        val icon = BitmapDrawable(resources, Glyphs.tinted(glyph, dp(22), Color.WHITE))
        weatherChip.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
        weatherChip.compoundDrawablePadding = dp(8)
        weatherChip.text = "${r.tempC}°  $text"
    }

    private fun requestWeatherPermissionOrRefresh() {
        if (Weather.hasPermission(this)) refreshWeather(force = true)
        else requestPermissions(arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION), 1)
    }

    private fun refreshWeather(force: Boolean = false) {
        val cached = Weather.cached(this)
        if (cached != null) showWeather(cached)
        if (!Weather.hasPermission(this)) {
            if (cached == null) weatherChip.text = "Weather: tap to allow location"
            if (!prefs.getBoolean("askedLocation", false)) {
                prefs.edit().putBoolean("askedLocation", true).apply()
                requestPermissions(arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION), 1)
            }
            return
        }
        if (force || Weather.isStale(cached)) {
            if (cached == null) weatherChip.text = "Weather: loading…"
            Weather.refresh(this) { r ->
                if (r != null) showWeather(r)
                else if (cached == null) weatherChip.text = "Weather unavailable"
            }
        }
    }

    // ---- dynamic island -------------------------------------------------------------------

    private fun hasNotificationAccess() =
        Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?.contains(packageName) == true

    private fun islandItems(live: List<IslandItem>): List<IslandItem> {
        if (live.isNotEmpty() || hasNotificationAccess() || prefs.getBoolean("setupSeen", false)) return live
        return listOf(IslandItem("setup", IslandKind.SETUP, packageName, "Enable island"))
    }

    private fun pushIsland() = island.setItems(islandItems(IslandRepository.items))

    // ---- black-and-white icons ------------------------------------------------------------

    /** A frosted white-lavender glass tile with the app's outlined glyph in deep purple. */
    private fun monoIcon(app: AppEntry): BitmapDrawable = iconCache.getOrPut(app.packageName) {
        val size = dp(56)
        val s = size.toFloat()
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val radius = s * Design.TILE_RADIUS
        val bounds = RectF(0f, 0f, s, s)
        val tile = Path().apply { addRoundRect(bounds, radius, radius, Path.Direction.CW) }

        // Glass: near-white body with a lavender cast, a violet glow at the base, a bright sheen on top.
        canvas.save()
        canvas.clipPath(tile)
        canvas.drawRect(bounds, Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, s,
                Color.argb(238, 252, 248, 255), Color.argb(206, 214, 194, 250), Shader.TileMode.CLAMP
            )
        })
        canvas.drawRect(RectF(0f, s * 0.55f, s, s), Paint().apply {
            shader = LinearGradient(
                0f, s * 0.55f, 0f, s,
                Color.argb(0, 150, 100, 235), Color.argb(80, 150, 100, 235), Shader.TileMode.CLAMP
            )
        })
        canvas.drawRect(RectF(0f, 0f, s, s * 0.5f), Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, s * 0.5f,
                Color.argb(120, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP
            )
        })
        canvas.restore()
        canvas.drawRoundRect(
            RectF(1f, 1f, s - 1f, s - 1f), radius, radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                shader = LinearGradient(
                    0f, 0f, 0f, s,
                    Color.argb(255, 255, 255, 255), Color.argb(110, 226, 206, 255), Shader.TileMode.CLAMP
                )
            }
        )

        // Glyph: deep purple, slightly lighter toward the base, with a soft violet shadow.
        val mask = Glyphs.mask(Glyphs.forApp(app.label, app.packageName), size)
        val shadow = Glyphs.mask(Glyphs.forApp(app.label, app.packageName), size)
        Canvas(mask).drawRect(0f, 0f, s, s, Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, s,
                Color.argb(255, 52, 18, 112), Color.argb(255, 118, 76, 196), Shader.TileMode.CLAMP
            )
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        })
        canvas.drawBitmap(shadow, 0f, dp(1).toFloat(), Paint().apply {
            maskFilter = BlurMaskFilter(dp(2).toFloat(), BlurMaskFilter.Blur.NORMAL)
            colorFilter = PorterDuffColorFilter(Color.argb(90, 90, 50, 160), PorterDuff.Mode.SRC_IN)
        })
        canvas.drawBitmap(mask, 0f, 0f, null)
        BitmapDrawable(resources, out)
    }
}
