package com.zyrovent.glauncher.launch

import android.content.Context
import android.os.Build
import java.io.File
import java.io.RandomAccessFile

/**
 * Selecciona el wrapper OpenGL que toca para la GPU del dispositivo.
 *
 * gl4es traduce OpenGL fijo (1.2/1.4 de escritorio, el que usa Minecraft con
 * LWJGL 2) a OpenGL ES. No es una adaptación libre: cada GPU tiene sus
 * manías y a lo largo de las pruebas en los equipos de la mesa salieron tres
 * perfiles distintos:
 *
 *  - **default**: perfil de escritorio con bits de profundidad concretos, el
 *    que funciona en Adreno y Mali (A53, portable).
 *  - **powervr**: la Adreno 506 del portátil rechazaba `RGBA8` en some FBOs y
 *    el mundo salía **negro con una esfera de neón**; aquí se pide
 *    `RGBA4/RGB5_A1` y se apagan los FBOs con MSAA, que es lo que arrancó
 *    ese equipo (registro: `Device identified: Adreno (TM) 506`).
 *  - **mali**: perfil conservador para los Mali-T (V83A) y los Mali-G de los
 *    gama media, con `RGB565` y sin `GL_ARB_texture_float`.
 */
object GpuProfiles {

    private const val PREFS = "glauncher_gpu"
    private const val KEY = "profile"

    /** Perfil forzado desde Ajustes; vacío = automático. */
    fun forced(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "") ?: ""

    fun setForced(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, value).apply()
    }

    /** Nombre de perfil y motivo, para el registro. */
    fun resolve(context: Context): Pair<String, String> {
        val forced = forced(context)
        if (forced.isNotBlank()) return forced to "forzado en Ajustes"

        val renderer = rendererName()
        val gpu = Build.HARDWARE.orEmpty() + " " + Build.DEVICE.orEmpty()

        val rule = when {
            renderer.contains("adreno", true) && renderer.contains("5", true) ->
                "powervr" to "Adreno 5xx detectada"

            renderer.contains("powervr", true) || renderer.contains("sgx", true) ->
                "powervr" to "GPU PowerVR"

            renderer.contains("mali", true) ||
                gpu.contains("kirin", true) ||
                gpu.contains("mt", true) && gpu.contains("67", true) ->
                "mali" to "GPU Mali / MediaTek"

            else -> "default" to "perfil genérico"
        }
        return rule
    }

    /**
     * Renderizador real de OpenGL ES reportado por el sistema (EGL_VENDOR /
     * EGL_RENDERER). Se lee por `dumpsys` porque `GLES20.glGetString` exige un
     * contexto creado.
     */
    private fun rendererName(): String = runCatching {
        val process = ProcessBuilder("dumpsys", "SurfaceFlinger")
            .redirectErrorStream(true)
            .start()
        val text = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        listOf("GL_RENDERER", "GL_RENDERER=").firstNotNullOfOrNull { key ->
            text.lineSequence()
                .firstOrNull { it.contains(key, ignoreCase = true) }
                ?.substringAfter("=")
                ?.trim()
        } ?: ""
    }.getOrDefault("")

    /** Cabecera ELF de un .so: `EI_CLASS` (2 = 64 bits) y `e_machine` (0xB7 = aarch64). */
    fun elfInfo(file: File): String = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            val ident = ByteArray(16)
            raf.readFully(ident)
            if (String(ident, 0, 4) != "ELF") return "no es ELF"
            raf.seek(18)
            val machine = ByteArray(2)
            raf.readFully(machine)
            val arch = (machine[0].toInt() and 0xFF) or ((machine[1].toInt() and 0xFF) shl 8)
            if (arch == 0xB7) "aarch64" else "otra arquitectura (0x${arch.toString(16)})"
        }
    }.getOrDefault(" ilegible")
}