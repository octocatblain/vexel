package com.vexel.launcher

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class IslandListenerService : NotificationListenerService() {
    override fun onListenerConnected() = IslandRepository.onConnected(this)
    override fun onListenerDisconnected() = IslandRepository.onDisconnected()
    override fun onNotificationPosted(sbn: StatusBarNotification) = IslandRepository.onPosted(sbn)
    override fun onNotificationRemoved(sbn: StatusBarNotification) = IslandRepository.onRemoved(sbn)
}
