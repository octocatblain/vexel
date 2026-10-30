package com.vexel.launcher

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Order is display priority: earlier kinds win the island. */
enum class IslandKind { ALARM, CALL, RIDE, TIMER, NAV, MEDIA, SETUP }

data class IslandItem(
    val key: String,
    val kind: IslandKind,
    val pkg: String,
    val title: String,
    val subtitle: String = "",
    val art: Bitmap? = null,
    val playing: Boolean = false,
    val duration: Long = 0,
    val position: Long = 0,
    val positionTime: Long = 0,
    val speed: Float = 1f,
    val controller: MediaController? = null,
    val tap: PendingIntent? = null,
    val actions: List<Notification.Action> = emptyList(),
    val postedAt: Long = 0
)

/**
 * Collects what the island can show: media sessions (Spotify etc.) plus alarm, call,
 * ride-hailing and timer notifications, fed by [IslandListenerService].
 */
object IslandRepository {
    var items: List<IslandItem> = emptyList()
        private set
    var onChange: ((List<IslandItem>) -> Unit)? = null

    private val RIDE_PKGS = setOf("ee.mtakso.client", "com.ubercab", "com.craftsilicon.littlecabrider")
    private val CLOCK_PKGS = setOf("com.android.deskclock", "com.google.android.deskclock")
    private const val PAUSED_LINGER_MS = 30_000L

    private val main = Handler(Looper.getMainLooper())
    private var ctx: Context? = null
    private var msm: MediaSessionManager? = null
    private val notes = LinkedHashMap<String, IslandItem>()
    private val mediaArt = HashMap<String, Bitmap>()
    private val pausedSince = HashMap<String, Long>()
    private var controllers: List<MediaController> = emptyList()
    private val callbacks = HashMap<MediaController, MediaController.Callback>()
    private val refresh = Runnable { publish() }

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { setControllers(it ?: emptyList()) }

    fun onConnected(service: NotificationListenerService) {
        val c = service.applicationContext
        ctx = c
        val manager = c.getSystemService(MediaSessionManager::class.java)
        msm = manager
        val component = ComponentName(service, IslandListenerService::class.java)
        try {
            manager.addOnActiveSessionsChangedListener(sessionsListener, component, main)
            setControllers(manager.getActiveSessions(component))
        } catch (e: SecurityException) {
            // Notification access not granted yet.
        }
        try {
            service.activeNotifications?.forEach { handle(it) }
        } catch (e: Exception) {
        }
        publish()
    }

    fun onDisconnected() {
        msm?.removeOnActiveSessionsChangedListener(sessionsListener)
        setControllers(emptyList())
        notes.clear()
        publish()
    }

    fun onPosted(sbn: StatusBarNotification) {
        handle(sbn)
        publish()
    }

    fun onRemoved(sbn: StatusBarNotification) {
        notes.remove(sbn.key)
        publish()
    }

    private fun setControllers(list: List<MediaController>) {
        callbacks.forEach { (c, cb) -> c.unregisterCallback(cb) }
        callbacks.clear()
        controllers = list
        list.forEach { c ->
            val cb = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
                override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
                override fun onSessionDestroyed() = publish()
            }
            c.registerCallback(cb, main)
            callbacks[c] = cb
        }
        publish()
    }

    private fun handle(sbn: StatusBarNotification) {
        val n = sbn.notification
        val pkg = sbn.packageName
        notes.remove(sbn.key)
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = n.extras
        if (extras.getString(Notification.EXTRA_TEMPLATE)?.endsWith("MediaStyle") == true) {
            // Remember the artwork; the media session supplies the rest.
            val c = ctx
            val icon = n.getLargeIcon()
            if (c != null && icon != null) icon.loadDrawable(c)?.let { mediaArt[pkg] = it.toBitmap() }
            return
        }

        val ongoing = n.flags and Notification.FLAG_ONGOING_EVENT != 0
        val kind = when {
            n.category == Notification.CATEGORY_ALARM -> IslandKind.ALARM
            n.category == Notification.CATEGORY_CALL -> IslandKind.CALL
            pkg in RIDE_PKGS && (ongoing || n.category in setOf(
                Notification.CATEGORY_STATUS, Notification.CATEGORY_TRANSPORT, Notification.CATEGORY_PROGRESS
            )) -> IslandKind.RIDE
            pkg in CLOCK_PKGS && ongoing -> IslandKind.TIMER
            n.category == Notification.CATEGORY_NAVIGATION -> IslandKind.NAV
            else -> return
        }

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isEmpty() && text.isEmpty()) return

        notes[sbn.key] = IslandItem(
            key = sbn.key,
            kind = kind,
            pkg = pkg,
            title = title.ifEmpty { text },
            subtitle = if (title.isEmpty()) "" else text,
            tap = n.contentIntent,
            actions = n.actions?.filter { it.actionIntent != null && it.title != null }.orEmpty(),
            postedAt = sbn.postTime
        )
    }

    private fun mediaItems(): List<IslandItem> {
        val now = SystemClock.elapsedRealtime()
        val out = ArrayList<IslandItem>()
        val live = HashSet<String>()
        var needsRecheck = false
        for (c in controllers) {
            val ps = c.playbackState ?: continue
            val md = c.metadata ?: continue
            val title = md.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: continue
            val playing = ps.state == PlaybackState.STATE_PLAYING || ps.state == PlaybackState.STATE_BUFFERING
            if (!playing && ps.state != PlaybackState.STATE_PAUSED) continue
            val pkg = c.packageName
            live += pkg
            if (playing) {
                pausedSince.remove(pkg)
            } else {
                val since = pausedSince.getOrPut(pkg) { now }
                if (now - since > PAUSED_LINGER_MS) continue
                needsRecheck = true
            }
            val art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
                ?: mediaArt[pkg]
            out += IslandItem(
                key = "media:$pkg",
                kind = IslandKind.MEDIA,
                pkg = pkg,
                title = title,
                subtitle = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE).orEmpty(),
                art = art,
                playing = playing,
                duration = md.getLong(MediaMetadata.METADATA_KEY_DURATION),
                position = ps.position,
                positionTime = ps.lastPositionUpdateTime,
                speed = ps.playbackSpeed,
                controller = c
            )
        }
        pausedSince.keys.retainAll(live)
        main.removeCallbacks(refresh)
        if (needsRecheck) main.postDelayed(refresh, PAUSED_LINGER_MS + 500)
        return out.sortedWith(compareBy({ !it.playing }, { it.pkg != "com.spotify.music" }))
    }

    private fun publish() {
        items = (notes.values + mediaItems())
            .sortedWith(compareBy({ it.kind.ordinal }, { -it.postedAt }))
        onChange?.invoke(items)
    }
}
