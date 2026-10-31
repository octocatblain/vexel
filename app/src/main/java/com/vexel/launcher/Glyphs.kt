package com.vexel.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RectF
import androidx.core.graphics.PathParser

/**
 * The launcher's own icon set: one 24-unit grid, one 2-unit stroke, round caps and joins,
 * outlined only. Every app is mapped to a glyph by metaphor so the set reads as one family.
 */
object Glyphs {
    private const val GRID = Design.GRID
    private const val STROKE = Design.STROKE

    /** Collects the strokes (outline) and dots (small fills) of one glyph, in grid units. */
    private class Shape {
        val outline = Path()
        val dots = Path()

        /** Filled shapes (with the same round stroke), for active-state glyphs like play and pause. */
        val solid = Path()

        fun solidPath(d: String) {
            try {
                solid.addPath(PathParser.createPathFromPathData(d))
            } catch (e: RuntimeException) {
                // A malformed path just leaves that part of the glyph out.
            }
        }

        fun solidRect(x: Float, y: Float, w: Float, h: Float, r: Float) =
            solid.addRoundRect(RectF(x, y, x + w, y + h), r, r, Path.Direction.CW)

        fun path(d: String, scale: Float = 1f, dx: Float = 0f, dy: Float = 0f) {
            try {
                val p = PathParser.createPathFromPathData(d)
                if (scale != 1f || dx != 0f || dy != 0f) {
                    p.transform(Matrix().apply {
                        setScale(scale, scale)
                        postTranslate(dx, dy)
                    })
                }
                outline.addPath(p)
            } catch (e: RuntimeException) {
                // A malformed path just leaves that part of the glyph out.
            }
        }

        fun circle(cx: Float, cy: Float, r: Float) = outline.addCircle(cx, cy, r, Path.Direction.CW)

        fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) =
            outline.addRoundRect(RectF(x, y, x + w, y + h), r, r, Path.Direction.CW)

        fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
            outline.moveTo(x1, y1)
            outline.lineTo(x2, y2)
        }

        fun dot(cx: Float, cy: Float, r: Float = 1.1f) = dots.addCircle(cx, cy, r, Path.Direction.CW)
    }

    private const val PHONE_PATH =
        "M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 " +
            "19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 " +
            "2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 " +
            "12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"

    /** The speech bubble both WhatsApp apps share: a round bubble with its tail at bottom left. */
    private const val BUBBLE_PATH = "M7.9 20A9 9 0 1 0 4 16.1L2 22z"

    private val glyphs: Map<String, Shape.() -> Unit> = mapOf(
        "phone" to { path(PHONE_PATH) },
        "message" to { path("M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z") },
        "chat" to {
            path(
                "M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7" +
                    "a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8z"
            )
        },
        "globe" to {
            circle(12f, 12f, 10f)
            line(2f, 12f, 22f, 12f)
            path("M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z")
        },
        "camera" to {
            path("M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z")
            circle(12f, 13f, 4f)
        },
        "insta" to {
            rect(3f, 3f, 18f, 18f, 5f)
            circle(12f, 12f, 4f)
            dot(17.5f, 6.5f, 1f)
        },
        "video" to {
            rect(2f, 5f, 20f, 14f, 4f)
            path("M10 9l5 3-5 3z")
        },
        "bag" to {
            path("M6 2L3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z")
            line(3f, 6f, 21f, 6f)
            path("M16 10a4 4 0 0 1-8 0")
        },
        "pin" to {
            path("M21 10c0 7-9 13-9 13S3 17 3 10a9 9 0 0 1 18 0z")
            circle(12f, 10f, 3f)
        },
        "calendar" to {
            rect(3f, 4f, 18f, 18f, 2f)
            line(16f, 2f, 16f, 6f)
            line(8f, 2f, 8f, 6f)
            line(3f, 10f, 21f, 10f)
        },
        "clock" to {
            circle(12f, 12f, 10f)
            path("M12 6v6l4 2")
        },
        "mail" to {
            rect(2f, 4f, 20f, 16f, 2f)
            path("M22 7l-10 7L2 7")
        },
        "image" to {
            rect(3f, 3f, 18f, 18f, 2f)
            circle(8.5f, 8.5f, 1.5f)
            path("M21 15l-5-5L5 21")
        },
        "folder" to {
            path("M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z")
        },
        "file" to {
            path("M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z")
            path("M13 2v7h7")
        },
        "download" to {
            path("M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4")
            path("M7 10l5 5 5-5")
            line(12f, 15f, 12f, 3f)
        },
        "sliders" to {
            line(4f, 21f, 4f, 14f)
            line(4f, 10f, 4f, 3f)
            line(12f, 21f, 12f, 12f)
            line(12f, 8f, 12f, 3f)
            line(20f, 21f, 20f, 16f)
            line(20f, 12f, 20f, 3f)
            line(1f, 14f, 7f, 14f)
            line(9f, 8f, 15f, 8f)
            line(17f, 16f, 23f, 16f)
        },
        "calc" to {
            rect(4f, 2f, 16f, 20f, 3f)
            line(8f, 7f, 16f, 7f)
            for (y in listOf(12f, 16f)) for (x in listOf(8f, 12f, 16f)) dot(x, y, 1f)
            dot(8f, 19f, 1f)
            dot(12f, 19f, 1f)
        },
        "note" to {
            line(12f, 20f, 21f, 20f)
            path("M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4z")
        },
        "cloud" to { path("M18 10h-1.26A8 8 0 1 0 9 20h9a5 5 0 0 0 0-10z") },
        "shield" to { path("M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z") },
        "droplet" to { path("M12 2.69l5.66 5.66a8 8 0 1 1-11.31 0z") },
        "lock" to {
            rect(3f, 11f, 18f, 11f, 2f)
            path("M7 11V7a5 5 0 0 1 10 0v4")
        },
        "layers" to {
            path("M12 2L2 7l10 5 10-5-10-5z")
            path("M2 17l10 5 10-5")
            path("M2 12l10 5 10-5")
        },
        "headphones" to {
            path("M3 18v-6a9 9 0 0 1 18 0v6")
            path("M21 19a2 2 0 0 1-2 2h-1a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2h3z")
            path("M3 19a2 2 0 0 0 2 2h1a2 2 0 0 0 2-2v-3a2 2 0 0 0-2-2H3z")
        },
        "music" to {
            path("M9 18V5l12-2v13")
            circle(6f, 18f, 3f)
            circle(18f, 16f, 3f)
        },
        "user" to {
            path("M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2")
            circle(12f, 7f, 4f)
        },
        "users" to {
            path("M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2")
            circle(9f, 7f, 4f)
            path("M23 21v-2a4 4 0 0 0-3-3.87")
            path("M16 3.13a4 4 0 0 1 0 7.75")
        },
        "briefcase" to {
            rect(2f, 7f, 20f, 14f, 2f)
            path("M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16")
        },
        "bolt" to { path("M13 2L3 14h9l-1 8 10-12h-9z") },
        "car" to {
            path("M5 11l1.5-4.5A2 2 0 0 1 8.4 5h7.2a2 2 0 0 1 1.9 1.5L19 11")
            rect(3f, 11f, 18f, 7f, 2f)
            dot(7.5f, 14.5f)
            dot(16.5f, 14.5f)
            line(6f, 18f, 6f, 20f)
            line(18f, 18f, 18f, 20f)
        },
        "cart" to {
            path("M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6")
            circle(9f, 20f, 1f)
            circle(20f, 20f, 1f)
        },
        "sim" to {
            path("M14 2H7a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7z")
            rect(9f, 11f, 6f, 7f, 1f)
            line(9f, 14.5f, 15f, 14.5f)
        },
        "card" to {
            rect(2f, 5f, 20f, 14f, 2f)
            line(2f, 10f, 22f, 10f)
            line(6f, 15f, 10f, 15f)
        },
        "wifi" to {
            path("M5 12.55a11 11 0 0 1 14.08 0")
            path("M1.42 9a16 16 0 0 1 21.16 0")
            path("M8.53 16.11a6 6 0 0 1 6.95 0")
            dot(12f, 20f, 1.2f)
        },
        "help" to {
            circle(12f, 12f, 10f)
            path("M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3")
            dot(12f, 17f, 1.1f)
        },
        "spark" to {
            line(12f, 3f, 12f, 21f)
            line(3f, 12f, 21f, 12f)
            line(5.6f, 5.6f, 18.4f, 18.4f)
            line(18.4f, 5.6f, 5.6f, 18.4f)
        },
        "gamepad" to {
            rect(2f, 7f, 20f, 12f, 6f)
            line(7f, 11f, 7f, 15f)
            line(5f, 13f, 9f, 13f)
            dot(16f, 12f, 1f)
            dot(18.5f, 14.5f, 1f)
        },
        "remote" to {
            rect(7f, 2f, 10f, 20f, 3f)
            circle(12f, 8f, 2f)
            line(10f, 14f, 14f, 14f)
            line(10f, 17.5f, 14f, 17.5f)
        },
        "sun" to {
            circle(12f, 12f, 4f)
            line(12f, 1f, 12f, 3f)
            line(12f, 21f, 12f, 23f)
            line(4.22f, 4.22f, 5.64f, 5.64f)
            line(18.36f, 18.36f, 19.78f, 19.78f)
            line(1f, 12f, 3f, 12f)
            line(21f, 12f, 23f, 12f)
            line(4.22f, 19.78f, 5.64f, 18.36f)
            line(18.36f, 5.64f, 19.78f, 4.22f)
        },
        "rain" to {
            path("M20 16.58A5 5 0 0 0 18 7h-1.26A8 8 0 1 0 4 15.25")
            line(8f, 19f, 8f, 21f)
            line(8f, 13f, 8f, 15f)
            line(16f, 19f, 16f, 21f)
            line(16f, 13f, 16f, 15f)
            line(12f, 21f, 12f, 23f)
            line(12f, 15f, 12f, 17f)
        },
        "snow" to {
            path("M20 17.58A5 5 0 0 0 18 8h-1.26A8 8 0 1 0 4 16.25")
            dot(8f, 16f, 1f)
            dot(8f, 20f, 1f)
            dot(12f, 18f, 1f)
            dot(12f, 22f, 1f)
            dot(16f, 16f, 1f)
            dot(16f, 20f, 1f)
        },
        "storm" to {
            path("M19 16.9A5 5 0 0 0 18 7h-1.26a8 8 0 1 0-11.62 9")
            path("M13 11l-4 6h6l-4 6")
        },
        "fog" to {
            line(3f, 8f, 21f, 8f)
            line(5f, 12f, 19f, 12f)
            line(3f, 16f, 21f, 16f)
            line(7f, 20f, 17f, 20f)
        },
        // Solid shapes are wound clockwise: Skia leaves a hole in counter-clockwise fill-and-stroke paths.
        // Brand glyphs: the app's own logo shape, redrawn in the set's stroke.
        "spotify" to {
            circle(12f, 12f, 10f)
            path("M6.8 9c3.6-1 7.8-.6 10.8 1.2")
            path("M7.6 12.6c3-.8 6.2-.5 8.6 1")
            path("M8.4 16c2.3-.6 4.5-.4 6.5.8")
        },
        "whatsapp" to {
            path(BUBBLE_PATH)
            // The phone glyph shrunk to sit in the middle of the bubble.
            path(PHONE_PATH, scale = 0.4f, dx = 7.4f, dy = 6.8f)
        },
        "wabusiness" to {
            path(BUBBLE_PATH)
            path("M10.2 8.2v7.2")
            path("M10.2 8.2h2.5a1.8 1.8 0 0 1 0 3.6h-2.5")
            path("M10.2 11.8h3a1.8 1.8 0 0 1 0 3.6h-3")
        },
        "healthshield" to {
            path("M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z")
            line(12f, 8.5f, 12f, 14.5f)
            line(9f, 11.5f, 15f, 11.5f)
        },
        "tiktok" to {
            path("M14 3v11.5a3.5 3.5 0 1 1-3.5-3.5")
            path("M14 3c.3 2.4 2 4 4.5 4.2")
        },
        "linkedin" to {
            rect(3f, 3f, 18f, 18f, 3f)
            dot(8f, 8.2f, 1f)
            line(8f, 11f, 8f, 17f)
            path("M12 17v-6")
            path("M12 13.2c0-1.4 1-2.2 2.3-2.2s2.7.8 2.7 2.7V17")
        },
        "teams" to {
            rect(2f, 5f, 14f, 14f, 3f)
            line(5.5f, 9f, 12.5f, 9f)
            line(9f, 9f, 9f, 15.5f)
            circle(19f, 8f, 2f)
            path("M17.5 12.5h4v4a2 2 0 0 1-4 0z")
        },
        "bike" to {
            circle(6f, 16f, 3.5f)
            circle(18f, 16f, 3.5f)
            path("M6 16l3.5-7h5.5l3 7")
            path("M9.5 9L8 6.5H6")
            line(15f, 9f, 13f, 16f)
        },
        "flashlight" to {
            path("M18 6c0 2-2 2-2 4v10a2 2 0 0 1-2 2h-4a2 2 0 0 1-2-2V10c0-2-2-2-2-4V2h12z")
            line(6f, 6f, 18f, 6f)
            dot(12f, 13f, 1f)
        },
        "play" to { solidPath("M8 5.5l11 6.5-11 6.5z") },
        "pause" to {
            solidRect(7f, 5.5f, 3f, 13f, 1f)
            solidRect(14f, 5.5f, 3f, 13f, 1f)
        },
        "next" to {
            solidPath("M5 6l9 6-9 6z")
            solidRect(17.5f, 6f, 2f, 12f, 1f)
        },
        "prev" to {
            solidPath("M19 6v12l-9-6z")
            solidRect(4.5f, 6f, 2f, 12f, 1f)
        },
        "apps" to {
            rect(3f, 3f, 7f, 7f, 2f)
            rect(14f, 3f, 7f, 7f, 2f)
            rect(3f, 14f, 7f, 7f, 2f)
            rect(14f, 14f, 7f, 7f, 2f)
        }
    )

    /** Picks a glyph name for an app from its label and package. Metaphor first, brand second. */
    fun forApp(label: String, pkg: String): String {
        val l = label.lowercase()
        val p = pkg.lowercase()
        fun has(vararg keys: String) = keys.any { l.contains(it) || p.contains(it) }
        return when {
            has("remote") -> "remote"
            has("wa business", "w4b") -> "wabusiness"
            has("whatsapp") -> "whatsapp"
            has("message", "mms", "sms") -> "message"
            has("safaricom") -> "sim"
            has("phone", "dialer") -> "phone"
            has("contact") -> "user"
            has("teams") -> "teams"
            has("linkedin") -> "linkedin"
            has("mail") -> "mail"
            has("chrome", "browser") -> "globe"
            has("instagram") -> "insta"
            has("camera") -> "camera"
            has("gallery", "photos") -> "image"
            has("maps") -> "pin"
            has("calendar") -> "calendar"
            has("clock") -> "clock"
            has("calculator") -> "calc"
            has("notes", "memo") -> "note"
            has("file manager", "explorer") -> "folder"
            has("files") -> "file"
            has("download") -> "download"
            has("settings") -> "sliders"
            has("weather") -> "cloud"
            has("security") -> "shield"
            has("theme") -> "droplet"
            has("authenticator") -> "lock"
            has("vault") -> "layers"
            has("spotify") -> "spotify"
            has("tiktok") -> "tiktok"
            has("music") -> "music"
            has("youtube", "video") -> "video"
            has("play store", "vending") -> "bag"
            has("bolt") -> "bolt"
            has("little", "taxi", "uber") -> "car"
            has("carrefour") -> "cart"
            has("loop") -> "card"
            has("smartaccess") -> "healthshield"
            has("feedback", "services") -> "help"
            has("claude") -> "spark"
            has("bike", "mountain") -> "bike"
            has("game") -> "gamepad"
            else -> "apps"
        }
    }

    /** A glyph filling most of a [size] square in a single [color], for use beside text. */
    fun tinted(name: String, size: Int, color: Int, boxRatio: Float = 0.9f): Bitmap =
        mask(name, size, boxRatio).also { Canvas(it).drawColor(color, PorterDuff.Mode.SRC_IN) }

    /** Opaque-black glyph on a transparent [size] square, ready to be tinted. */
    fun mask(name: String, size: Int, boxRatio: Float = Design.GLYPH_BOX): Bitmap {
        val shape = Shape().apply((glyphs[name] ?: glyphs.getValue("apps")))
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val box = size * boxRatio
        canvas.translate((size - box) / 2f, (size - box) / 2f)
        canvas.scale(box / GRID, box / GRID)
        canvas.drawPath(shape.outline, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.BLACK
        })
        canvas.drawPath(shape.dots, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK })
        canvas.drawPath(shape.solid, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL_AND_STROKE
            strokeWidth = STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            color = Color.BLACK
        })
        return out
    }
}
