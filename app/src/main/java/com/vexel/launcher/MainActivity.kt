package com.vexel.launcher

import android.app.Activity
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextClock
import android.widget.TextView

private data class AppEntry(
    val label: String,
    val packageName: String,
    val intent: Intent,
    val info: ResolveInfo
)

class MainActivity : Activity() {

    private var allApps: List<AppEntry> = emptyList()
    private var shown: List<AppEntry> = emptyList()
    private val iconCache = HashMap<String, BitmapDrawable>()
    private lateinit var adapter: AppAdapter
    private lateinit var search: EditText

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(dp(16), dp(32), dp(16), dp(8))
        }

        root.addView(TextClock(this).apply {
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
            setTextColor(Color.WHITE)
            textSize = 56f
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
        })
        root.addView(TextClock(this).apply {
            format12Hour = "EEEE, d MMMM"
            format24Hour = "EEEE, d MMMM"
            setTextColor(Color.LTGRAY)
            textSize = 16f
        })

        search = EditText(this).apply {
            hint = "Search apps"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setSingleLine()
            background = roundedBg(Color.rgb(28, 28, 28), dp(24).toFloat())
            setPadding(dp(20), dp(12), dp(20), dp(12))
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) = filter(s?.toString().orEmpty())
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        root.addView(
            search,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(20); bottomMargin = dp(12) }
        )

        adapter = AppAdapter()
        val grid = GridView(this).apply {
            numColumns = 4
            verticalSpacing = dp(16)
            selector = ColorDrawable(Color.TRANSPARENT)
            this.adapter = this@MainActivity.adapter
            setOnItemClickListener { _, _, pos, _ -> startActivity(shown[pos].intent) }
            setOnItemLongClickListener { v, _, pos, _ -> showMenu(v, shown[pos]); true }
        }
        root.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        loadApps()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        search.setText("")
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // A launcher has nowhere to go back to.
        search.setText("")
    }

    private fun loadApps() {
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val pm = packageManager
        iconCache.clear()
        allApps = pm.queryIntentActivities(main, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map {
                val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClassName(it.activityInfo.packageName, it.activityInfo.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName, i, it)
            }
            .sortedBy { it.label.lowercase() }
        filter(search.text.toString())
    }

    private fun filter(q: String) {
        shown = if (q.isBlank()) allApps else allApps.filter { it.label.contains(q, ignoreCase = true) }
        adapter.notifyDataSetChanged()
    }

    private fun showMenu(anchor: View, app: AppEntry) {
        PopupMenu(this, anchor).apply {
            menu.add("App info")
            menu.add("Uninstall")
            setOnMenuItemClickListener {
                val uri = Uri.parse("package:${app.packageName}")
                if (it.title == "App info") {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
                } else {
                    startActivity(Intent(Intent.ACTION_DELETE, uri))
                }
                true
            }
            show()
        }
    }

    private fun roundedBg(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

    /** Draws the app icon as a black-and-white rounded tile. */
    private fun monoIcon(app: AppEntry): BitmapDrawable = iconCache.getOrPut(app.packageName) {
        val size = dp(56)
        val src = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val d = app.info.loadIcon(packageManager)
        d.setBounds(0, 0, size, size)
        d.draw(Canvas(src))

        // Grayscale with extra contrast so colours collapse towards black or white.
        val gray = ColorMatrix().apply { setSaturation(0f) }
        val c = 1.8f
        val t = (-0.5f * c + 0.5f) * 255f
        gray.postConcat(
            ColorMatrix(
                floatArrayOf(
                    c, 0f, 0f, 0f, t,
                    0f, c, 0f, 0f, t,
                    0f, 0f, c, 0f, t,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val radius = size * 0.28f
        val bounds = RectF(0f, 0f, size.toFloat(), size.toFloat())
        canvas.clipPath(Path().apply { addRoundRect(bounds, radius, radius, Path.Direction.CW) })
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(
            src, 0f, 0f,
            Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(gray) }
        )
        BitmapDrawable(resources, out)
    }

    private inner class AppAdapter : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int) = shown[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val app = shown[position]
            val cell = (convertView as? LinearLayout) ?: LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(ImageView(context), LinearLayout.LayoutParams(dp(56), dp(56)))
                addView(
                    TextView(context).apply {
                        setTextColor(Color.WHITE)
                        textSize = 12f
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        gravity = Gravity.CENTER
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(6) }
                )
            }
            (cell.getChildAt(0) as ImageView).setImageDrawable(monoIcon(app))
            (cell.getChildAt(1) as TextView).text = app.label
            return cell
        }
    }
}
