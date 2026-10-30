package com.vexel.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
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

private data class AppEntry(
    val label: String,
    val packageName: String,
    val intent: Intent,
    val info: ResolveInfo
)

class MainActivity : Activity() {

    companion object {
        private const val COLS = 4
        private const val ROWS = 6
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
    private lateinit var search: EditText
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
        IslandRepository.onChange = { island.setItems(islandItems(it)) }
    }

    override fun onResume() {
        super.onResume()
        loadApps()
        pushIsland()
        refreshWeather()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        search.setText("")
        pager.setCurrentItem(0, true)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // A launcher has nowhere to go back to.
        if (search.text.isNotEmpty()) search.setText("") else pager.setCurrentItem(0, true)
    }

    override fun onDestroy() {
        IslandRepository.onChange = null
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        refreshWeather()
    }

    // ---- UI construction ------------------------------------------------------------------

    private fun buildUi() {
        root = FrameLayout(this)
        root.setBackgroundColor(scrim(0f))

        homeContent = buildHome()
        pagerAdapter = PagerAdapter()
        pager = ViewPager2(this).apply {
            adapter = pagerAdapter
            offscreenPageLimit = 1
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageScrolled(position: Int, offset: Float, px: Int) {
                    val p = (position + offset).coerceIn(0f, 1f)
                    root.setBackgroundColor(scrim(p))
                    search.alpha = p
                    search.visibility = if (p > 0.02f) View.VISIBLE else View.INVISIBLE
                }

                override fun onPageSelected(position: Int) {
                    refreshDots()
                    if (position == 0) hideKeyboard()
                }
            })
        }
        root.addView(pager, FrameLayout.LayoutParams(MATCH, MATCH))

        search = EditText(this).apply {
            hint = "Search apps"
            setHintTextColor(Color.argb(170, 255, 255, 255))
            setTextColor(Color.WHITE)
            setSingleLine()
            background = glass(dp(26).toFloat())
            setPadding(dp(20), 0, dp(20), 0)
            visibility = View.INVISIBLE
            alpha = 0f
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) = filter(s?.toString().orEmpty())
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        root.addView(search, FrameLayout.LayoutParams(MATCH, dp(52)).apply {
            marginStart = dp(16)
            marginEnd = dp(16)
        })

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
        (search.layoutParams as FrameLayout.LayoutParams).topMargin = topInset + dp(64)
        (dots.layoutParams as FrameLayout.LayoutParams).bottomMargin = bottomInset + dp(14)
        (homeInfo.layoutParams as FrameLayout.LayoutParams).topMargin = topInset + dp(72)
        (dock.layoutParams as FrameLayout.LayoutParams).bottomMargin = bottomInset + dp(44)
        root.requestLayout()
        pagerAdapter.notifyDataSetChanged()
    }

    private fun scrim(p: Float): Int = Color.argb((85 + 130 * p).toInt(), 0, 0, 0)

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
            background = glass(dp(36).toFloat())
            setPadding(dp(10), dp(14), dp(10), dp(14))
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
            }, LinearLayout.LayoutParams(dp(56), dp(56)))
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
            setPadding(dp(12), topInset + dp(132), dp(12), bottomInset + dp(44))
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
        setPadding(0, dp(6), 0, 0)
        addView(ImageView(context).apply { setImageDrawable(monoIcon(app)) }, LinearLayout.LayoutParams(dp(56), dp(56)))
        addView(TextView(context).apply {
            text = app.label
            setTextColor(Color.WHITE)
            textSize = 12f
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
        filter(search.text.toString())
    }

    private fun filter(q: String) {
        shown = if (q.isBlank()) allApps else allApps.filter { it.label.contains(q, ignoreCase = true) }
        pagerAdapter.notifyDataSetChanged()
        refreshDots()
    }

    private fun resolveFavorites(): List<AppEntry> {
        val saved = prefs.getString("favs", null)?.split("|")?.filter { it.isNotEmpty() }
        val names = saved ?: defaultFavorites()
        return names.mapNotNull { n -> allApps.firstOrNull { it.packageName == n } }.take(MAX_FAVORITES)
    }

    private fun defaultFavorites(): List<String> {
        val probes = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        )
        val picks = probes
            .mapNotNull { packageManager.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName }
            .filter { it != "android" }
            .toMutableList()
        picks += "com.android.vending"
        picks += allApps.map { it.packageName }
        return picks.distinct().filter { p -> allApps.any { it.packageName == p } }.take(MAX_FAVORITES)
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
        val (symbol, text) = Weather.describe(r.code)
        weatherChip.text = "$symbol  ${r.tempC}°  $text"
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
        val icon = app.info.loadIcon(packageManager)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val radius = s * 0.3f
        val bounds = RectF(0f, 0f, s, s)

        // Glass tile: smoky grey body, bright top sheen, soft light edge.
        val tile = Path().apply { addRoundRect(bounds, radius, radius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(tile)
        canvas.drawRect(bounds, Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, s,
                intArrayOf(Color.argb(150, 120, 124, 134), Color.argb(130, 52, 54, 62)),
                null, Shader.TileMode.CLAMP
            )
        })
        canvas.drawRect(RectF(0f, 0f, s, s * 0.5f), Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, s * 0.5f,
                Color.argb(70, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP
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
                    Color.argb(150, 255, 255, 255), Color.argb(30, 255, 255, 255), Shader.TileMode.CLAMP
                )
            }
        )

        // Glyph mask (opaque where the symbol is), then filled with a white-to-silver gradient.
        val mask = (icon as? AdaptiveIconDrawable)?.foreground?.let { silhouette(it, size) }
            ?: detailMask(icon, size)
        Canvas(mask).drawRect(0f, 0f, s, s, Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, s,
                Color.argb(255, 255, 255, 255), Color.argb(255, 188, 190, 198), Shader.TileMode.CLAMP
            )
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        })
        canvas.drawBitmap(mask, 0f, 0f, null)
        BitmapDrawable(resources, out)
    }

    /** Mask of the dark/minority detail of a non-adaptive icon. */
    private fun detailMask(icon: Drawable, size: Int): Bitmap {
        val src = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, size, size)
        icon.draw(Canvas(src))
        val px = IntArray(size * size)
        src.getPixels(px, 0, size, 0, 0, size, size)
        val lum = IntArray(px.size) {
            val p = px[it]
            if (Color.alpha(p) < 128) 255
            else (0.299f * Color.red(p) + 0.587f * Color.green(p) + 0.114f * Color.blue(p)).toInt()
        }
        // Split at the mean brightness; the smaller region is the symbol.
        val mean = lum.average()
        val invert = lum.count { it < mean } > lum.size / 2
        for (i in px.indices) {
            px[i] = if ((lum[i] < mean) != invert) Color.WHITE else Color.TRANSPARENT
        }
        src.setPixels(px, 0, size, 0, 0, size, size)
        return src
    }

    /** Black silhouette of an adaptive icon's foreground, or null if it is mostly solid. */
    private fun silhouette(fg: Drawable, size: Int): Bitmap? {
        // Adaptive layers are 108dp with a 72dp visible area; draw at 1.5x so the visible part fills the tile.
        val layer = (size * 1.5f).toInt()
        val inset = (layer - size) / 2
        val raw = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        fg.setBounds(-inset, -inset, layer - inset, layer - inset)
        fg.draw(Canvas(raw))
        val px = IntArray(size * size)
        raw.getPixels(px, 0, size, 0, 0, size, size)
        val opaque = px.count { Color.alpha(it) > 200 }
        if (opaque == 0 || opaque > px.size * 0.5) return null
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val shrink = size * 0.14f
        Canvas(out).drawBitmap(
            raw, null, RectF(shrink, shrink, size - shrink, size - shrink),
            Paint(Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = android.graphics.PorterDuffColorFilter(Color.BLACK, android.graphics.PorterDuff.Mode.SRC_IN)
            }
        )
        return out
    }
}
