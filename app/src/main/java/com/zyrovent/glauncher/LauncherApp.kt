package com.zyrovent.glauncher

import android.app.Application
import android.util.Log

class LauncherApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Hay que hacerlo antes de lanzar ningún proceso Java: Android 12+ exige
        // la etiqueta en el byte superior de los punteros y OpenJDK aborta con
        // "Pointer tag ... was truncated" (SIGABRT 134) en cuanto corre la GC.
        runCatching {
            System.loadLibrary("glauncher")
            val disabled = disablePointerTagging()
            Log.i(TAG, "Tagging de punteros desactivado: $disabled")
        }.onFailure {
            Log.e(TAG, "No se pudo cargar la librería nativa", it)
        }

        extractBundledComponents()
    }

    private fun extractBundledComponents() {
        // El stub de LWJGL es el puente GLFW; cacio-androidnw aporta
        // net.java.openjdk.cacio.ctc.ExternalMouseReader, que el stub referencia
        // al arrancar las versiones antiguas (sin él: NoClassDefFoundError → SIGSEGV).
        val components = listOf(
            "lwjgl-glfw-stub.jar",
            "caciocavallo/cacio-androidnw-1.10-SNAPSHOT.jar"
        )
        components.forEach { relative ->
            runCatching {
                val componentsDir = filesDir.resolve("components")
                val target = componentsDir.resolve(relative.substringAfterLast('/'))
                target.parentFile?.mkdirs()
                assets.open("components/$relative").use { input ->
                    if (!target.exists() || target.length() != input.available().toLong()) {
                        target.outputStream().use { output -> input.copyTo(output) }
                        Log.i(TAG, "Componente extraído: ${target.name} (${target.length()} bytes)")
                    }
                }
            }.onFailure {
                Log.e(TAG, "Error al extraer el componente $relative", it)
            }
        }
    }

    private external fun disablePointerTagging(): Boolean

    companion object {
        private const val TAG = "GLauncher"

        /** Contexto global para los almacenes que no reciben uno (playlists, etc.). */
        lateinit var instance: LauncherApp
            private set
    }
}
