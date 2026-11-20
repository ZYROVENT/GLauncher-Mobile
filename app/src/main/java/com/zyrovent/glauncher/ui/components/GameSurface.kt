package com.zyrovent.glauncher.ui.components

import android.graphics.SurfaceTexture
import android.util.Log
import android.view.TextureView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.zyrovent.glauncher.launch.GameWindow
import com.zyrovent.glauncher.launch.RenderTuning
import kotlin.math.roundToInt

/**
 * Superficie donde se renderiza el cliente.
 *
 * Se usa TextureView y no SurfaceView a propósito: SurfaceView hace un agujero
 * en la ventana y siempre queda por encima del contenido, así que durante la
 * carga taparía la interfaz del launcher con un rectángulo negro. La textura se
 * comporta como una vista normal: respeta el orden de capas y solo se ve cuando
 * la partida ocupa la pantalla.
 *
 * Al crearse entrega su superficie al puente EGL para que gl4es abra el
 * contexto sobre ella; al destruirse se libera.
 */
@Composable
fun GameSurface(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            // La Surface propia se conserva viva mientras la textura exista.
            var surface: android.view.Surface? = null
            // Escala de render del equipo: se recalcula al conocer el tamaño real
            // del panel (en pantallas pequeñas hay un mínimo por legibilidad).
            var scale = RenderTuning.scale(context)

            fun bufferSize(width: Int, height: Int): Pair<Int, Int> =
                (width * scale).roundToInt().coerceAtLeast(2) to
                    (height * scale).roundToInt().coerceAtLeast(2)

            TextureView(context).apply {
                // La partida no debe apagar la pantalla.
                keepScreenOn = true
                // El juego pinta opaco: si el compositor sabe que la capa no
                // tiene transparencia se ahorra mezclar 1,1 M de píxeles por
                // fotograma. Es gratis y en esta GPU se nota.
                isOpaque = true
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        texture: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        releaseSurface(surface)
                        // Sin esto el búfer de la SurfaceTexture nace en 1x1 y el
                        // juego dibuja fuera de la pantalla: se ve todo negro.
                        // Al reducirlo se renderiza menos píxeles y la GPU lo
                        // estira a pantalla completa (ver RenderTuning).
                        scale = RenderTuning.scale(context, width, height)
                        val (bufferW, bufferH) = bufferSize(width, height)
                        GameWindow.setRenderScale(scale)
                        runCatching { texture.setDefaultBufferSize(bufferW, bufferH) }
                        Log.i(
                            "GLauncher",
                            RenderTuning.describe(scale, width, height)
                        )
                        surface = android.view.Surface(texture).also {
                            GameWindow.attach(it, bufferW, bufferH)
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(
                        texture: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        scale = RenderTuning.scale(context, width, height)
                        val (bufferW, bufferH) = bufferSize(width, height)
                        GameWindow.setRenderScale(scale)
                        runCatching { texture.setDefaultBufferSize(bufferW, bufferH) }
                        // Notificar el cambio de resolución al puente EGL / GLFW
                        GameWindow.setSize(bufferW, bufferH)
                        if (!GameWindow.ready) {
                            releaseSurface(surface)
                            surface = android.view.Surface(texture).also {
                                GameWindow.attach(it, bufferW, bufferH)
                            }
                        }
                    }

                    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                        // Solo desvincular si la instancia no sigue en marcha
                        if (!com.zyrovent.glauncher.launch.InProcessLaunch.running) {
                            GameWindow.detach()
                            releaseSurface(surface)
                            surface = null
                            return true
                        }
                        // Mantener la textura viva durante transiciones de pantalla
                        return false
                    }

                    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                }
            }
        }
    )
}

private fun releaseSurface(surface: android.view.Surface?) {
    runCatching { surface?.release() }
}
