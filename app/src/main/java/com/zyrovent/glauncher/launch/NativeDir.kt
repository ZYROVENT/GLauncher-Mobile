package com.zyrovent.glauncher.launch

import java.io.File

/**
 * Directorio donde se sueltan las .so de gl4es y del puente antes de cargarlas.
 *
 * En Android 10+ (API 29) el linker exige que las bibliotecas habituales (las
 * del propio APK, y con `extractNativeLibs=false` también las de `/system`)
 * estén **alineadas a 16 KB** para poderlas mapear. Las .so de gl4es vienen
 * alineadas a 4 KB, así que se copian a este directorio —que no está en la
 * lista de rutas habituales— y desde ahí el linker las acepta igual.
 */
object NativeDir {
    fun dir(app: android.content.Context): File =
        File(app.filesDir, "natives").apply { mkdirs() }

    /**
     * Copia [name] (p. ej. `libgl4es_114.so`) desde los assets a la carpeta de
     * nativas y devuelve la ruta absoluta, o null si no existe en los assets.
     */
    fun extract(app: android.content.Context, name: String): String? = runCatching {
        val target = File(dir(app), name)
        if (!target.exists() || target.length() == 0L) {
            app.assets.open(name).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            target.setReadable(true, false)
            target.setExecutable(true, false)
        }
        target.absolutePath
    }.getOrNull()
}