package com.vexel.launcher

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Current weather from Open-Meteo (no API key) at the phone's coarse location. */
object Weather {
    data class Reading(val tempC: Int, val code: Int, val time: Long)

    private const val STALE_MS = 20 * 60 * 1000L

    fun cached(context: Context): Reading? {
        val p = context.getSharedPreferences("vexel", Context.MODE_PRIVATE)
        if (!p.contains("wx_time")) return null
        return Reading(p.getInt("wx_temp", 0), p.getInt("wx_code", 0), p.getLong("wx_time", 0))
    }

    fun isStale(r: Reading?) = r == null || System.currentTimeMillis() - r.time > STALE_MS

    fun hasPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Fetches fresh weather and calls [done] on the main thread (null on failure). */
    fun refresh(context: Context, done: (Reading?) -> Unit) {
        locate(context) { loc ->
            if (loc == null) return@locate done(null)
            Thread {
                val reading = try {
                    fetch(loc.latitude, loc.longitude)
                } catch (e: Exception) {
                    null
                }
                if (reading != null) {
                    context.getSharedPreferences("vexel", Context.MODE_PRIVATE).edit()
                        .putInt("wx_temp", reading.tempC)
                        .putInt("wx_code", reading.code)
                        .putLong("wx_time", reading.time)
                        .apply()
                }
                context.mainExecutor.execute { done(reading) }
            }.start()
        }
    }

    @SuppressLint("MissingPermission")
    private fun locate(context: Context, cb: (Location?) -> Unit) {
        if (!hasPermission(context)) return cb(null)
        val lm = context.getSystemService(LocationManager::class.java)
        val last = lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
        if (last != null) return cb(last)
        val provider = if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) LocationManager.NETWORK_PROVIDER
        else lm.getProviders(true).firstOrNull() ?: return cb(null)
        lm.getCurrentLocation(provider, null, context.mainExecutor) { cb(it) }
    }

    private fun fetch(lat: Double, lon: Double): Reading {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code"
        )
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        try {
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val cur = JSONObject(body).getJSONObject("current")
            return Reading(
                Math.round(cur.getDouble("temperature_2m")).toInt(),
                cur.getInt("weather_code"),
                System.currentTimeMillis()
            )
        } finally {
            conn.disconnect()
        }
    }

    /** Text-style symbol (︎ keeps it monochrome) and a short description for a WMO weather code. */
    fun describe(code: Int): Pair<String, String> = when (code) {
        0 -> "☀︎" to "Clear"
        1, 2 -> "⛅︎" to "Partly cloudy"
        3 -> "☁︎" to "Cloudy"
        45, 48 -> "☁︎" to "Fog"
        in 51..57 -> "☂︎" to "Drizzle"
        in 61..67, in 80..82 -> "☂︎" to "Rain"
        in 71..77, 85, 86 -> "❄︎" to "Snow"
        in 95..99 -> "⚡︎" to "Thunderstorm"
        else -> "☁︎" to "Weather"
    }
}
