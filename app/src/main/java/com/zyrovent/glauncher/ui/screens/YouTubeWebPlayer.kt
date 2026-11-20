package com.zyrovent.glauncher.ui.screens

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.zyrovent.glauncher.audio.GMusicWebBridge
import com.zyrovent.glauncher.audio.MusicPlaybackBridge
import com.zyrovent.glauncher.audio.MusicService

/**
 * Un Chrome de escritorio hace que YouTube sirva su reproductor completo; la
 * cadena tampoco lleva el flag `wv`, que es lo que activa los avisos de
 * "actualiza tu navegador" y bloquea el autoplay.
 */
private const val PLAYER_UA =
    "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/120.0.0.0 Safari/537.36"

/**
 * Se ejecuta en cada página del reproductor.
 *
 * La vía rápida es `/embed/`: pesa una fracción de la página normal y el vídeo
 * suena en menos de un segundo. Si el dueño del vídeo bloqueó la inserción, el
 * propio script salta a la página de siempre a los 5 s. En ambos casos engancha
 * los eventos del `<video>` para informar al launcher del estado real
 * (`playing`, `buffering`, `paused`…).
 */
private fun watchUrl(videoId: String): String =
    "https://m.youtube.com/watch?v=$videoId&autoplay=1&hl=es"

private fun engineScript(videoId: String): String = """
(function () {
  var report = function (name) {
    try { GMusicBridge.state(name); } catch (e) {}
  };

  function hook(v) {
    if (!v || v.__ghook) return;
    v.__ghook = true;
    try {
      v.muted = false;
      v.volume = 1.0;
      v.setAttribute('playsinline', '1');
      if (v.duration && !isNaN(v.duration)) {
        try { GMusicBridge.onDuration(v.duration); } catch (e) {}
      }
      v.addEventListener('durationchange', function () {
        if (v.duration && !isNaN(v.duration)) {
          try { GMusicBridge.onDuration(v.duration); } catch (e) {}
        }
      });
      v.addEventListener('timeupdate', function () {
        if (v.currentTime) {
          try { GMusicBridge.onTimeUpdate(v.currentTime); } catch (e) {}
        }
      });
      v.addEventListener('playing', function () { report('playing'); });
      v.addEventListener('waiting', function () { report('buffering'); });
      v.addEventListener('pause', function () { report('paused'); });
      v.addEventListener('ended', function () { report('ended'); });
      v.addEventListener('error', function () { report('error'); });
      var p = v.play();
      if (p && p.catch) p.catch(function () { report('blocked'); });
      report(v.paused ? 'buffering' : 'playing');
    } catch (e) {}
  }

  // Aceptar cookies/consentimiento automáticamente si aparece
  var btn = document.querySelector('button[aria-label*="Aceptar"], button[aria-label*="Accept"], tp-yt-paper-button#agree-button');
  if (btn && btn.click) btn.click();

  hook(document.querySelector('video'));

  var count = 0;
  var interval = setInterval(function () {
    count++;
    hook(document.querySelector('video'));
    var playBtn = document.querySelector('.ytp-large-play-button') || document.querySelector('button.player-control-play-pause-icon');
    if (playBtn && playBtn.click && count < 6) playBtn.click();
    if (count > 20) clearInterval(interval);
  }, 250);
})();
"""

private const val PLAY_JS = """
(function () {
  var v = document.querySelector('video');
  if (v) { v.muted = false; v.volume = 1.0; v.play(); }
})();
"""

private const val PAUSE_JS = """
(function () {
  var v = document.querySelector('video');
  if (v && !v.paused) v.pause();
})();
"""

private fun scriptFor(command: String): String = when (command) {
    "pause" -> PAUSE_JS
    "play" -> PLAY_JS
    else -> ""
}

/**
 * Motor de audio de GMusic: una WebView de 1 dp que reproduce solo el sonido.
 *
 * Vive aquí, en [com.zyrovent.glauncher.ui.LauncherRoot], y no se desmonta al
 * cambiar de pestaña, así que la música sigue sonando mientras se juega o se
 * revisan ajustes. Mientras suena, [MusicService] mantiene el proceso en primer
 * plano con una notificación de control, de modo que al salir del launcher el
 * audio continúa.
 *
 * La WebView se crea una sola vez (al abrir GMusic o al primer play) y desde ahí
 * solo cambia de vídeo: crear una nueva en cada canción era lo que hacía que
 * tardara tanto en arrancar.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MusicAudioEngine(
    vm: MusicViewModel,
    modifier: Modifier = Modifier,
    /** Hay una partida en curso: el motor no debe tocar la GPU del juego. */
    gameActive: Boolean = false
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    // Se queda con la última pista cargada aunque el usuario pulse DETENER:
    // así el WebView sigue montado y el siguiente play es instantáneo.
    val track = state.engineTrack
    val mountEngine = state.warmUp || track != null

    // Comandos desde la notificación o los auriculares.
    DisposableEffect(Unit) {
        MusicPlaybackBridge.onCommand = { command -> vm.onExternalCommand(command) }
        onDispose {
            if (MusicPlaybackBridge.onCommand != null) MusicPlaybackBridge.onCommand = null
        }
    }

    // Notificación de reproducción (y proceso en primer plano) mientras suena.
    LaunchedEffect(state.audioActive, state.current?.id, state.paused) {
        val current = state.current
        if (state.audioActive && current != null) {
            MusicService.show(
                context = context.applicationContext,
                title = current.title,
                subtitle = current.uploader,
                playing = !state.paused,
                thumbnailUrl = current.thumbnail,
                durationSeconds = current.seconds
            )
        } else {
            MusicService.hide(context.applicationContext)
        }
    }

    if (!mountEngine) return

    // Con Minecraft abierto el WebView pasa a dibujarse por software.
    //
    // Chromium componía su vídeo sobre la misma GPU que usa gl4es para el juego
    // y al empezar a reproducir se llevaba por delante la textura del juego
    // (pantallazo negro). En capa software no crea ninguna superficie gráfica:
    // el audio sigue igual y el render de Minecraft no se entera.
    val layerType = if (gameActive) View.LAYER_TYPE_SOFTWARE else View.LAYER_TYPE_HARDWARE

    Box(modifier.size(1.dp)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(Color.BLACK)
                    setLayerType(layerType, null)
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        cacheMode = WebSettings.LOAD_DEFAULT
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        useWideViewPort = false
                        loadWithOverviewMode = false
                        builtInZoomControls = false
                        displayZoomControls = false
                        setSupportMultipleWindows(false)
                        javaScriptCanOpenWindowsAutomatically = false
                        userAgentString = PLAYER_UA
                        blockNetworkImage = true
                    }
                    addJavascriptInterface(GMusicWebBridge(), "GMusicBridge")
                    // Que el renderer siga con prioridad alta en segundo plano:
                    // es lo que mantiene el audio vivo al salir del launcher.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            super.onPageFinished(view, url)
                            val id = view.tag as? String ?: return
                            if (id.isBlank()) return
                            // La página de YouTube es una SPA y monta el
                            // reproductor en varios pasos: se inyecta en cadena.
                            listOf(0L, 500L, 1_200L, 2_500L, 5_000L).forEach { delay ->
                                view.postDelayed(
                                    { runCatching { view.evaluateJavascript(engineScript(id), null) } },
                                    delay
                                )
                            }
                        }
                    }
                    MusicPlaybackBridge.attach(this)
                    if (track != null) {
                        tag = track.id
                        loadUrl(watchUrl(track.id))
                    }
                }
            },
            update = { web ->
                if (web.layerType != layerType) {
                    web.setLayerType(layerType, null)
                }
                val id = track?.id
                if (!state.audioActive) {
                    if (web.tag != null) {
                        web.tag = null
                        web.stopLoading()
                        web.loadUrl("about:blank")
                    }
                } else if (id != null && web.tag != id) {
                    web.stopLoading()
                    web.tag = id
                    web.loadUrl(watchUrl(id))
                }
            },
            onRelease = { web ->
                MusicPlaybackBridge.detach(web)
                runCatching {
                    web.stopLoading()
                    web.loadUrl("about:blank")
                    web.destroy()
                }
            }
        )
    }

    // Pausa / reanudación sin recargar la página.
    LaunchedEffect(state.commandSeq) {
        val js = scriptFor(state.command)
        if (state.commandSeq > 0 && js.isNotEmpty()) {
            MusicPlaybackBridge.evaluate(js)
        }
    }
}
