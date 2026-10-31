package com.vexel.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the lock screen service after a reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            try {
                context.startForegroundService(Intent(context, LockService::class.java))
            } catch (e: RuntimeException) {
                // Background start was refused; the next launcher start will bring it up.
            }
        }
    }
}
