package com.zyrovent.glauncher.launch

import com.zyrovent.glauncher.data.GamePaths
import java.io.File

/**
 * Prepara el directorio de natives que LWJGL va a buscar dentro del proceso del juego.
 *
 * El cliente desktop pide librerías de escritorio (libGLX.so.0 / libGL.so.1) que no
 * existen en Android. Aquí se copian al directorio privado interno de natives
 * (appContext.filesDir/natives) para que el dynamic linker de Android (bionic)
 * permita dlopen() sin violar las restricciones de namespaces de Android 7-10+.
 */
object NativeLibs {

    /** Nombres que LWJGL intenta cargar en Linux si no se fija el renderer. */
    private val GL_ALIASES = listOf("libGLX.so.0", "libGL.so.1", "libGL.so")

    /** Librería que implementa cada renderer ofrecido en Ajustes. */
    fun rendererLibrary(renderer: String): String = when (renderer) {
        "Zinc" -> "libOSMesa.so"
        else -> "libgl4es_114.so"
    }

    /** Copia las natives disponibles y monta los alias; devuelve el directorio privado interno. */
    fun prepare(paths: GamePaths, renderer: String): File {
        val dest = paths.nativesDir
        dest.mkdirs()

        paths.appLibDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".so") }
            ?.forEach { copyIfNeeded(it, dest.resolve(it.name)) }

        val rendererLib = dest.resolve(rendererLibrary(renderer)).takeIf { it.exists() }
            ?: dest.resolve("libgl4es_114.so").takeIf { it.exists() }

        if (rendererLib != null) {
            GL_ALIASES.forEach { alias -> copyIfNeeded(rendererLib, dest.resolve(alias)) }
        }
        return dest
    }

    private fun copyIfNeeded(source: File, target: File) {
        if (target.exists() && target.length() == source.length()) return
        if (target.exists()) target.delete()
        source.copyTo(target, overwrite = true)
    }
}
