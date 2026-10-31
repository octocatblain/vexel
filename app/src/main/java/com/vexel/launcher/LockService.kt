package com.vexel.launcher

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * Keeps the lock screen ready: when the screen turns on while the phone is locked it raises
 * [LockScreenActivity] over the system keyguard through a full-screen-intent notification,
 * the one route Android allows for starting an activity from the background.
 */
class LockService : Service() {

    companion object {
        private const val CHANNEL_SERVICE = "lock_service"
        private const val CHANNEL_LOCK = "lock_screen"
        private const val ID_SERVICE = 1
        private const val ID_LOCK = 2
        const val PREF_ENABLED = "lockScreen"
    }

    private val main = Handler(Looper.getMainLooper())

    private val screenOn = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = raiseLockScreen()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, "Lock screen", NotificationManager.IMPORTANCE_MIN)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_LOCK, "Lock screen display", NotificationManager.IMPORTANCE_HIGH)
        )
        val ongoing = Notification.Builder(this, CHANNEL_SERVICE)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("Vexel lock screen is on")
            .setOngoing(true)
            .build()
        startForeground(ID_SERVICE, ongoing, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenOn, filter, RECEIVER_NOT_EXPORTED)
        else registerReceiver(screenOn, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        unregisterReceiver(screenOn)
        super.onDestroy()
    }

    private fun raiseLockScreen() {
        val prefs = getSharedPreferences("vexel", MODE_PRIVATE)
        val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
        android.util.Log.d("VexelLock", "screen on: enabled=${prefs.getBoolean(PREF_ENABLED, true)} locked=$locked")
        if (!prefs.getBoolean(PREF_ENABLED, true)) return
        if (!locked) return

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, LockScreenActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(
            ID_LOCK,
            Notification.Builder(this, CHANNEL_LOCK)
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setContentTitle("Lock screen")
                .setCategory(Notification.CATEGORY_ALARM)
                .setFullScreenIntent(open, true)
                .setAutoCancel(true)
                .build()
        )
        main.postDelayed({ nm.cancel(ID_LOCK) }, 3000)
    }
}
