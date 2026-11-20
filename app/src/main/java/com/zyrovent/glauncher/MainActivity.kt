package com.zyrovent.glauncher

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.zyrovent.glauncher.ui.theme.GLauncherTheme
import com.zyrovent.glauncher.ui.LauncherRoot

/** Pantalla completa sin bordes: el contenido llega hasta debajo de las barras
 *  del sistema y las insets se aplican en Compose, no en la ventana. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Contenido a pantalla completa: las barras no empujan la interfaz.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Las barras se vuelven transparentes para que el fondo llegue al borde.
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

        // Modo inmersivo suave: se ocultan las barras y aparecen al deslizar.
        hideSystemBars()

        setContent {
            GLauncherTheme {
                LauncherRoot()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
