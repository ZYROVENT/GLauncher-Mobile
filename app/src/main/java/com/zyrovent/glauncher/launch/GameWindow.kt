package com.zyrovent.glauncher.launch

import android.view.Surface

/**
 * Ventana donde se dibuja Minecraft y puente de entrada hacia GLFW/Pojav.
 *
 * El renderer (gl4es + puente EGL de Pojav) necesita una `ANativeWindow` válida
 * antes de inicializar OpenGL, así que la Surface debe existir y estar enlazada
 * antes de lanzar el JVM.
 */
object GameWindow {

    init {
        System.loadLibrary("glauncher")
    }

    /** Constantes de teclas de GLFW */
    const val KEY_SPACE = 32
    const val KEY_A = 65
    const val KEY_D = 68
    const val KEY_E = 69
    const val KEY_Q = 81
    const val KEY_S = 83
    const val KEY_T = 84
    const val KEY_W = 87
    const val KEY_ESCAPE = 256
    const val KEY_F3 = 292
    const val KEY_F5 = 294
    const val KEY_LEFT_SHIFT = 340
    const val KEY_LEFT_CONTROL = 341

    /** Constantes de ratón de GLFW */
    const val MOUSE_BUTTON_LEFT = 0
    const val MOUSE_BUTTON_RIGHT = 1
    const val MOUSE_BUTTON_MIDDLE = 2

    /** Acciones */
    const val ACTION_RELEASE = 0
    const val ACTION_PRESS = 1
    const val ACTION_REPEAT = 2

    /** true cuando la Surface ya está enlazada con el puente EGL. */
    @Volatile
    var ready: Boolean = false
        private set

    /** Motivo del último fallo de enlace, para poder mostrarlo en el registro. */
    @Volatile
    var lastError: String? = null
        private set

    /**
     * Escala a la que se renderiza el juego respecto a la pantalla. El búfer de
     * la SurfaceTexture es más pequeño que la vista y la GPU lo estira, así que
     * las coordenadas de los toques hay que devolverlas al espacio del juego.
     */
    @Volatile
    var renderScale: Float = 1f
        private set

    fun setRenderScale(scale: Float) {
        renderScale = scale.coerceIn(0.3f, 1f)
    }

    private external fun attachSurface(surface: Surface): Boolean
    private external fun nativeIsGrabbing(): Boolean
    private external fun detachSurface()
    private external fun nativeSetWindowSize(width: Int, height: Int)
    private external fun nativeSetInputReady(ready: Boolean)

    private external fun nativeSendKey(key: Int, scancode: Int, action: Int, mods: Int)
    private external fun nativeSendMouseButton(button: Int, action: Int, mods: Int)
    private external fun nativeSendCursorPos(x: Float, y: Float)
    private external fun nativeSendChar(codepoint: Char)
    private external fun nativeSendScroll(xoffset: Double, yoffset: Double)

    fun attach(surface: Surface, width: Int = 0, height: Int = 0): Boolean {
        val attached = runCatching { attachSurface(surface) }.getOrDefault(false)
        ready = attached
        lastError = if (attached) null else "No se pudo enlazar la ventana del juego con el puente EGL"
        if (attached) {
            runCatching { nativeSetInputReady(true) }
            if (width > 0 && height > 0) {
                setSize(width, height)
            }
        }
        return attached
    }

    fun setSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        runCatching { nativeSetWindowSize(width, height) }
    }

    fun detach() {
        ready = false
        runCatching { nativeSetInputReady(false) }
        runCatching { detachSurface() }
    }

    fun sendKey(key: Int, isDown: Boolean, mods: Int = 0) {
        val action = if (isDown) ACTION_PRESS else ACTION_RELEASE
        runCatching { nativeSendKey(key, 0, action, mods) }
    }

    fun sendMouseButton(button: Int, isDown: Boolean, mods: Int = 0) {
        val action = if (isDown) ACTION_PRESS else ACTION_RELEASE
        runCatching { nativeSendMouseButton(button, action, mods) }
    }

    /** ¿Minecraft tiene el ratón capturado (jugando, no en un menú)? */
    fun isGrabbing(): Boolean = runCatching { nativeIsGrabbing() }.getOrDefault(false)

    fun sendCursorPos(x: Float, y: Float) {
        // La superficie se renderiza escalada: el juego trabaja en su propio
        // espacio de coordenadas, no en píxeles de pantalla.
        val scale = renderScale
        runCatching { nativeSendCursorPos(x * scale, y * scale) }
    }

    fun sendChar(codepoint: Char) {
        runCatching { nativeSendChar(codepoint) }
    }

    fun sendScroll(xoffset: Double, yoffset: Double) {
        runCatching { nativeSendScroll(xoffset, yoffset) }
    }
}
