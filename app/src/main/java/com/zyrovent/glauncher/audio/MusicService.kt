package com.zyrovent.glauncher.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.zyrovent.glauncher.MainActivity
import com.zyrovent.glauncher.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

/**
 * Lo que la WebView de GMusic cuenta de sí misma: `playing`, `buffering`,
 * `paused`, `ended`, `blocked` o `error`. Lo escribe el puente JavaScript
 * ([GMusicWebBridge]) y lo lee el ViewModel para pintar el estado real.
 */
object GMusicEngine {

    private val _state = MutableStateFlow("")
    val state: StateFlow<String> = _state.asStateFlow()

    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration.asStateFlow()

    private val _position = MutableStateFlow(0)
    val position: StateFlow<Int> = _position.asStateFlow()

    fun report(value: String) {
        _state.value = value
    }

    fun reportDuration(durSec: Int) {
        if (durSec > 0) _duration.value = durSec
    }

    fun reportPosition(posSec: Int) {
        _position.value = posSec
    }
}

/** Puente `window.GMusicBridge` dentro de la WebView del reproductor. */
class GMusicWebBridge(private val onTrackEnded: (() -> Unit)? = null) {

    @JavascriptInterface
    fun state(value: String) {
        GMusicEngine.report(value)
        if (value == "ended") {
            MusicPlaybackBridge.onCommand?.invoke("next")
        }
    }

    @JavascriptInterface
    fun onDuration(seconds: Float) {
        GMusicEngine.reportDuration(seconds.toInt())
    }

    @JavascriptInterface
    fun onTimeUpdate(seconds: Float) {
        GMusicEngine.reportPosition(seconds.toInt())
    }
}

/**
 * Puente entre la notificación (o los auriculares) y la WebView que suena.
 *
 * La WebView vive en la Activity, pero cuando el launcher queda en segundo plano
 * el servicio sigue en primer plano: ahí es donde [MusicService] necesita poder
 * mandarle pausa o play sin conocer Compose.
 */
object MusicPlaybackBridge {

    /** `play`, `pause` o `stop`; lo pone el motor de audio al montarse. */
    @Volatile
    var onCommand: ((String) -> Unit)? = null

    private var webView: WeakReference<WebView>? = null

    fun attach(view: WebView) {
        webView = WeakReference(view)
    }

    fun detach(view: WebView) {
        if (webView?.get() === view) webView = null
    }

    fun evaluate(js: String) {
        val view = webView?.get() ?: return
        view.post { runCatching { view.evaluateJavascript(js, null) } }
    }
}

/**
 * Servicio en primer plano que mantiene vivo el proceso mientras GMusic suena.
 *
 * Sin él, Android puede matar el launcher al salir y la WebView se lleva la
 * música. También publica la notificación con carátula y controles, para que se
 * pueda pausar o parar desde fuera sin volver al launcher.
 */
class MusicService : Service() {

    companion object {
        private const val CHANNEL_ID = "gmusic_playback"
        private const val NOTIFICATION_ID = 4210

        const val ACTION_SHOW = "com.zyrovent.glauncher.music.SHOW"
        const val ACTION_CMD_PLAY = "com.zyrovent.glauncher.music.CMD_PLAY"
        const val ACTION_CMD_PAUSE = "com.zyrovent.glauncher.music.CMD_PAUSE"
        const val ACTION_CMD_STOP = "com.zyrovent.glauncher.music.CMD_STOP"
        const val ACTION_CMD_NEXT = "com.zyrovent.glauncher.music.CMD_NEXT"
        const val ACTION_CMD_PREV = "com.zyrovent.glauncher.music.CMD_PREV"

        private const val EXTRA_TITLE = "title"
        private const val EXTRA_SUBTITLE = "subtitle"
        private const val EXTRA_PLAYING = "playing"
        private const val EXTRA_THUMBNAIL = "thumbnail"
        private const val EXTRA_DURATION = "duration"

        /** Muestra o actualiza la notificación con la pista actual. */
        fun show(
            context: Context,
            title: String,
            subtitle: String,
            playing: Boolean,
            thumbnailUrl: String? = null,
            durationSeconds: Int = 0
        ) {
            val intent = Intent(context, MusicService::class.java)
                .setAction(ACTION_SHOW)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_SUBTITLE, subtitle)
                .putExtra(EXTRA_PLAYING, playing)
                .putExtra(EXTRA_THUMBNAIL, thumbnailUrl)
                .putExtra(EXTRA_DURATION, durationSeconds)
            startForeground(context, intent)
        }

        /**
         * Baja la notificación y libera el primer plano. Se usa `stopService` y
         * no un `startForegroundService`: Android 8+ mata la app si se arranca
         * en primer plano y el servicio no llama a `startForeground` en 5 s.
         */
        fun hide(context: Context) {
            runCatching { context.stopService(Intent(context, MusicService::class.java)) }
        }

        private fun startForeground(context: Context, intent: Intent) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }
    }

    private var session: MediaSession? = null
    private var title: String = "GMusic"
    private var subtitle: String = ""
    private var playing: Boolean = true
    private var currentThumbnailUrl: String? = null
    private var thumbnailBitmap: Bitmap? = null
    private var durationSeconds: Int = 0

    override fun onCreate() {
        super.onCreate()
        createChannel()
        session = MediaSession(this, "GMusic").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = MusicPlaybackBridge.onCommand?.invoke("play") ?: Unit
                override fun onPause() = MusicPlaybackBridge.onCommand?.invoke("pause") ?: Unit
                override fun onStop() = MusicPlaybackBridge.onCommand?.invoke("stop") ?: Unit
                override fun onSkipToNext() = MusicPlaybackBridge.onCommand?.invoke("next") ?: Unit
                override fun onSkipToPrevious() = MusicPlaybackBridge.onCommand?.invoke("previous") ?: Unit
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CMD_PLAY -> MusicPlaybackBridge.onCommand?.invoke("play")
            ACTION_CMD_PAUSE -> MusicPlaybackBridge.onCommand?.invoke("pause")
            ACTION_CMD_STOP -> MusicPlaybackBridge.onCommand?.invoke("stop")
            ACTION_CMD_NEXT -> MusicPlaybackBridge.onCommand?.invoke("next")
            ACTION_CMD_PREV -> MusicPlaybackBridge.onCommand?.invoke("previous")
        }

        intent?.getStringExtra(EXTRA_TITLE)?.let { title = it }
        intent?.getStringExtra(EXTRA_SUBTITLE)?.let { subtitle = it }
        if (intent?.hasExtra(EXTRA_PLAYING) == true) {
            playing = intent.getBooleanExtra(EXTRA_PLAYING, true)
        }
        if (intent?.hasExtra(EXTRA_DURATION) == true) {
            durationSeconds = intent.getIntExtra(EXTRA_DURATION, 0)
        }

        val newThumb = intent?.getStringExtra(EXTRA_THUMBNAIL)
        if (newThumb != null && newThumb != currentThumbnailUrl) {
            currentThumbnailUrl = newThumb
            fetchThumbnail(newThumb)
        }

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        updateSessionState()
        return START_NOT_STICKY
    }

    private fun fetchThumbnail(url: String) {
        Thread {
            try {
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                conn.doInput = true
                conn.connect()
                val stream = conn.inputStream
                val bitmap = BitmapFactory.decodeStream(stream)
                stream.close()
                if (bitmap != null) {
                    thumbnailBitmap = bitmap
                    updateSessionState()
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(NOTIFICATION_ID, buildNotification())
                }
            } catch (e: Exception) {
                // Ignore failure for cover
            }
        }.start()
    }

    override fun onDestroy() {
        stopForeground(true)
        session?.isActive = false
        session?.release()
        session = null
        thumbnailBitmap = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateSessionState() {
        val current = session ?: return
        val state = if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        current.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_STOP or
                        PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackState.ACTION_PLAY_PAUSE
                )
                .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, if (playing) 1f else 0f)
                .build()
        )
        val metaBuilder = android.media.MediaMetadata.Builder()
            .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, subtitle)
        if (durationSeconds > 0) {
            metaBuilder.putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, durationSeconds * 1000L)
        }
        thumbnailBitmap?.let { bmp ->
            metaBuilder.putBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART, bmp)
            metaBuilder.putBitmap(android.media.MediaMetadata.METADATA_KEY_ART, bmp)
        }
        current.setMetadata(metaBuilder.build())
    }

    private fun pendingIntent(action: String): PendingIntent {
        val intent = Intent(this, MusicService::class.java).setAction(action)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getService(this, action.hashCode(), intent, flags)
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        // Acciones completas para MediaStyle
        val prevAction = Notification.Action.Builder(
            R.drawable.ic_skip_previous,
            "Anterior",
            pendingIntent(ACTION_CMD_PREV)
        ).build()

        val toggleAction = Notification.Action.Builder(
            if (playing) R.drawable.ic_pause else R.drawable.ic_play,
            if (playing) "Pausa" else "Reproducir",
            pendingIntent(if (playing) ACTION_CMD_PAUSE else ACTION_CMD_PLAY)
        ).build()

        val nextAction = Notification.Action.Builder(
            R.drawable.ic_skip_next,
            "Siguiente",
            pendingIntent(ACTION_CMD_NEXT)
        ).build()

        val stopAction = Notification.Action.Builder(
            R.drawable.ic_stop,
            "Detener",
            pendingIntent(ACTION_CMD_STOP)
        ).build()

        // Modo sin desplegar (compacto): muestra toggle y parar (o prev/toggle/next según Android)
        // Pedido por el usuario:
        // "su modo sin desplegar con 2 botones de parar y pausar" -> toggle (0) y stop (1)
        val mediaStyle = Notification.MediaStyle()
            .setMediaSession(session?.sessionToken)
            .setShowActionsInCompactView(0, 1)

        builder
            .setContentTitle(title)
            .setContentText(subtitle.ifBlank { "GMusic" })
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentIntent(openApp)
            .setOngoing(playing)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setStyle(mediaStyle)
            // Botón 0: Play/Pausa
            .addAction(toggleAction)
            // Botón 1: Detener
            .addAction(stopAction)
            // Botón 2: Anterior (visible al desplegar)
            .addAction(prevAction)
            // Botón 3: Siguiente (visible al desplegar)
            .addAction(nextAction)

        thumbnailBitmap?.let { bmp ->
            builder.setLargeIcon(bmp)
        }

        return builder.build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "GMusic",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Controles del reproductor mientras suena en segundo plano"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}
