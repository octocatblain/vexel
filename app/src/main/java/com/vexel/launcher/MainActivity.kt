package com.vexel.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.PorterDuffColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextClock
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs

private data class AppEntry(
    val label: String,
    val packageName: String,
    val intent: Intent,
    val info: ResolveInfo
)

class MainActivity : Activity() {

    companion object {
        private const val COLS = 5
        private const val ROWS = 9
        private const val PER_PAGE = COLS * ROWS
        private const val MAX_FAVORITES = 5
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }

    private val prefs by lazy { getSharedPreferences("vexel", MODE_PRIVATE) }

    private var allApps: List<AppEntry> = emptyList()
    private var shown: List<AppEntry> = emptyList()
    private var favorites: List<AppEntry> = emptyList()
    private val iconCache = HashMap<String, BitmapDrawable>()

    private var topInset = 0
    private var bottomInset = 0

    private lateinit var root: FrameLayout
    private lateinit var pager: ViewPager2
    private lateinit var pagerAdapter: PagerAdapter
    private lateinit var dots: LinearLayout
    private lateinit var island: IslandView

    private lateinit var homeContent: FrameLayout
    private lateinit var homeInfo: LinearLayout
    private lateinit var dock: LinearLayout
    private lateinit var weatherChip: TextView

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

    override fun onResume() {
        super.onResume()
        loadApps()
        pushIsland()
        refreshWeather()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        pager.setCurrentItem(0, true)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // A launcher has nowhere to go back to.
        pager.setCurrentItem(0, true)
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

    /** Swipe up on home opens the apps, swipe down on home pulls the notification shade, swipe down on apps returns home. */
    private var downX = 0f
    private var downY = 0f

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
            }
            MotionEvent.ACTION_UP -> onVerticalSwipe(ev.x - downX, ev.y - downY)
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun onVerticalSwipe(dx: Float, dy: Float) {
        if (abs(dy) < dp(80) || abs(dy) < abs(dx) * 1.5f) return
        val onHome = pager.currentItem == 0
        when {
            dy < 0 && onHome -> pager.setCurrentItem(1, true)
            dy > 0 && onHome -> expandNotifications()
            dy > 0 -> pager.setCurrentItem(0, true)
        }
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
        val backdrop = Backdrop(this)
        root.addView(backdrop, FrameLayout.LayoutParams(MATCH, MATCH))

        homeContent = buildHome()
        pagerAdapter = PagerAdapter()
        pager = ViewPager2(this).apply {
            adapter = pagerAdapter
            offscreenPageLimit = 1
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageScrolled(position: Int, offset: Float, px: Int) {
                    val p = (position + offset).coerceIn(0f, 1f)
                    backdrop.progress = p
                }

                override fun onPageSelected(position: Int) {
                    refreshDots()
                    if (position == 0) hideKeyboard()
                }
            })
        }
        root.addView(pager, FrameLayout.LayoutParams(MATCH, MATCH))

        dots = LinearLayout(this).apply { gravity = Gravity.CENTER }
        root.addView(dots, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))

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
        (dots.layoutParams as FrameLayout.LayoutParams).bottomMargin = bottomInset + dp(14)
        (homeInfo.layoutParams as FrameLayout.LayoutParams).topMargin = topInset + dp(72)
        (dock.layoutParams as FrameLayout.LayoutParams).bottomMargin = bottomInset + dp(36)
        root.requestLayout()
        pagerAdapter.notifyDataSetChanged()
    }

    private fun buildHome(): FrameLayout {
        val home = FrameLayout(this)

        homeInfo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), 0, dp(28), 0)
        }
        homeInfo.addView(TextClock(this).apply {
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
            setTextColor(Color.WHITE)
            textSize = 84f
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            includeFontPadding = false
            setShadowLayer(12f, 0f, 2f, Color.argb(120, 0, 0, 0))
        })
        homeInfo.addView(TextClock(this).apply {
            format12Hour = "EEEE, d MMMM"
            format24Hour = "EEEE, d MMMM"
            setTextColor(Color.WHITE)
            textSize = 20f
            setShadowLayer(8f, 0f, 1f, Color.argb(120, 0, 0, 0))
        })
        weatherChip = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            background = glass(dp(22).toFloat())
            setPadding(dp(16), dp(8), dp(16), dp(8))
            text = "Weather: tap to allow location"
            setOnClickListener { requestWeatherPermissionOrRefresh() }
        }
        homeInfo.addView(weatherChip, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(16) })
        home.addView(homeInfo, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP))

        dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = glassDock(dp(38).toFloat())
            setPadding(dp(8), dp(16), dp(8), dp(16))
        }
        home.addView(dock, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM).apply {
            marginStart = dp(16)
            marginEnd = dp(16)
        })
        return home
    }

    private fun refreshDock() {
        dock.removeAllViews()
        favorites.forEach { app ->
            val cell = LinearLayout(this).apply { gravity = Gravity.CENTER }
            cell.addView(ImageView(this).apply {
                setImageDrawable(monoIcon(app))
                setOnClickListener { startActivity(app.intent) }
                setOnLongClickListener { showMenu(this, app); true }
            }, LinearLayout.LayoutParams(dp(60), dp(60)))
            dock.addView(cell, LinearLayout.LayoutParams(0, WRAP, 1f))
        }
    }

    private fun refreshDots() {
        dots.removeAllViews()
        val total = pagerAdapter.itemCount
        if (total <= 1) return
        for (i in 0 until total) {
            dots.addView(View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (i == pager.currentItem) Color.WHITE else Color.argb(90, 255, 255, 255))
                }
            }, LinearLayout.LayoutParams(dp(7), dp(7)).apply { marginStart = dp(4); marginEnd = dp(4) })
        }
    }

    private fun hideKeyboard() {
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(root.windowToken, 0)
    }

    // ---- pager ----------------------------------------------------------------------------

    private fun pageCount() = (shown.size + PER_PAGE - 1) / PER_PAGE

    private inner class PagerAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = 1 + pageCount()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val frame = FrameLayout(parent.context)
            frame.layoutParams = RecyclerView.LayoutParams(MATCH, MATCH)
            return object : RecyclerView.ViewHolder(frame) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val frame = holder.itemView as FrameLayout
            frame.removeAllViews()
            if (position == 0) {
                (homeContent.parent as? ViewGroup)?.removeView(homeContent)
                frame.addView(homeContent, FrameLayout.LayoutParams(MATCH, MATCH))
            } else {
                frame.addView(gridPage(position - 1), FrameLayout.LayoutParams(MATCH, MATCH))
            }
        }
    }

    private fun gridPage(page: Int): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), topInset + dp(56), dp(8), bottomInset + dp(34))
        }
        for (r in 0 until ROWS) {
            val row = LinearLayout(this)
            for (c in 0 until COLS) {
                val i = page * PER_PAGE + r * COLS + c
                row.addView(if (i < shown.size) appCell(shown[i]) else View(this), LinearLayout.LayoutParams(0, MATCH, 1f))
            }
            col.addView(row, LinearLayout.LayoutParams(MATCH, 0, 1f))
        }
        return col
    }

    private fun appCell(app: AppEntry) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(0, dp(2), 0, 0)
        addView(ImageView(context).apply { setImageDrawable(monoIcon(app)) }, LinearLayout.LayoutParams(dp(56), dp(56)))
        addView(TextView(context).apply {
            text = app.label
            setTextColor(Color.WHITE)
            textSize = 11f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setShadowLayer(6f, 0f, 1f, Color.BLACK)
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
        shown = allApps
        pagerAdapter.notifyDataSetChanged()
        refreshDots()
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

    /** Draws the app icon as a frosted-glass grey tile with a light monochrome glyph. */
    private fun monoIcon(app: AppEntry): BitmapDrawable = iconCache.getOrPut(app.packageName) {
        val size = dp(56)
        val s = size.toFloat()
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val radius = s * Design.TILE_RADIUS
        val bounds = RectF(0f, 0f, s, s)

        // Lavender glass tile: milky body, soft inner shade, bright top sheen.
        val tile = Path().apply { addRoundRect(bounds, radius, radius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(tile)
        canvas.drawRect(bounds, Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, s,
                intArrayOf(Color.argb(252, 246, 240, 255), Color.argb(226, 208, 190, 246)),
                null, Shader.TileMode.CLAMP
            )
        })
        canvas.drawRect(RectF(0f, s * 0.6f, s, s), Paint().apply {
            shader = LinearGradient(
                0f, s * 0.6f, 0f, s,
                Color.argb(0, 150, 110, 215), Color.argb(80, 150, 110, 215), Shader.TileMode.CLAMP
            )
        })
        canvas.drawRect(RectF(0f, 0f, s, s * 0.5f), Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, s * 0.5f,
                Color.argb(130, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP
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
                    Color.argb(255, 255, 255, 255), Color.argb(110, 255, 255, 255), Shader.TileMode.CLAMP
                )
            }
        )

        // Glyph mask (opaque where the symbol is), then filled with a deep-purple gradient.
        val mask = Glyphs.mask(Glyphs.forApp(app.label, app.packageName), size)
        Canvas(mask).drawRect(0f, 0f, s, s, Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, s,
                Color.argb(250, 52, 18, 112), Color.argb(250, 118, 76, 196), Shader.TileMode.CLAMP
            )
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        })
        // Soft shadow under the symbol, then the symbol itself.
        canvas.drawBitmap(mask, 0f, dp(1).toFloat(), Paint().apply {
            maskFilter = BlurMaskFilter(dp(2).toFloat(), BlurMaskFilter.Blur.NORMAL)
            colorFilter = PorterDuffColorFilter(Color.argb(90, 90, 50, 160), PorterDuff.Mode.SRC_IN)
        })
        canvas.drawBitmap(mask, 0f, 0f, null)
        BitmapDrawable(resources, out)
    }
}
