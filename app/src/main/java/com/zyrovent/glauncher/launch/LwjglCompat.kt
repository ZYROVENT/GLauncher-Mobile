package com.zyrovent.glauncher.launch

import com.zyrovent.glauncher.data.GamePaths
import com.zyrovent.glauncher.data.VersionFiles
import java.io.File

/**
 * Compatibilidad con Minecraft antiguo (1.12.2 y anteriores).
 *
 * Esas versiones usan **LWJGL 2**, cuyas nativas solo existen compiladas para
 * escritorio; Android no puede cargarlas. La solución que usan los launchers de
 * referencia es `lwjglx`: un jar en Java puro que reimplementa la API de LWJGL 2
 * sobre LWJGL 3 (que sí está en la APK, en `jniLibs`).
 *
 * Con el shim en el classpath hay que **quitar** los jars de LWJGL 2 originales,
 * porque si no las clases viejas ganan y el juego pide `liblwjgl.so` de escritorio.
 *
 * Si el shim no está, el juego se instala y arranca igual, pero el registro dice
 * exactamente qué falta en vez de morir con un `UnsatisfiedLinkError` críptico.
 */
object LwjglCompat {

    /** Nombre del jar dentro de `assets/components/`. */
    private const val SHIM_ASSET = "components/lwjglx.jar"

    /** true si la versión es 1.12.2 o anterior (usa LWJGL 2 sin excepciones). */
    fun versionUsesLwjgl2(versionId: String): Boolean {
        val id = versionId.substringBefore('-').trim()
        if (!id.startsWith("1.")) return false
        val parts = id.split('.')
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: return false
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        return minor < 13 || (minor == 12 && patch <= 2)
    }

    /**
     * Generación de LWJGL del cliente: 2 para 1.12.2 y antes, 3 para 1.13+.
     * Se mira el classpath porque es la fuente fiable; el id es el respaldo.
     */
    fun generation(files: VersionFiles): Int {
        val porLibrerias = files.libraries.any { library ->
            library.path.startsWith("lwjgl/lwjgl/") ||
                library.path.startsWith("lwjgl/lwjgl_util/")
        }
        return when {
            porLibrerias -> 2
            versionUsesLwjgl2(files.id) -> 2
            else -> 3
        }
    }

    /**
     * El stub de GLFW que ya viene con la app incluye también las clases de
     * LWJGL 2 (`org.lwjgl.input.*`), así que las versiones viejas no necesitan
     * nada extra: se detecta mirando dentro del jar.
     */
    fun hasBuiltInShim(paths: GamePaths): Boolean {
        val jar = paths.componentsDir.resolve("lwjgl-glfw-stub.jar")
        if (!jar.exists()) return false
        return runCatching {
            java.util.zip.ZipFile(jar).use { zip ->
                zip.getEntry("org/lwjgl/input/Mouse.class") != null &&
                    zip.getEntry("org/lwjgl/input/Keyboard.class") != null
            }
        }.getOrDefault(false)
    }

    /** Jar del shim ya disponible (copiado desde assets), o null. */
    fun shim(paths: GamePaths): File? {
        val installed = paths.componentsDir.resolve("lwjglx.jar")
        if (installed.exists() && installed.length() > 0) return installed
        return runCatching {
            paths.componentsDir.mkdirs()
            paths.appContext.assets.open(SHIM_ASSET).use { input ->
                installed.outputStream().use { output -> input.copyTo(output) }
            }
            installed.takeIf { it.length() > 0 }
        }.getOrNull()
    }

    /** Jars de LWJGL 2 que el shim reemplaza y que no deben ir en el classpath. */
    fun replacedJars(library: String): Boolean =
        library.startsWith("lwjgl/lwjgl/") ||
            library.startsWith("lwjgl/lwjgl_util/") ||
            library.startsWith("org/lwjgl/lwjgl/2")

    /**
     * Prepara la capa de compatibilidad. Devuelve el jar a poner primero en el
     * classpath, o null si esta versión no la necesita / no está disponible.
     */
    fun prepare(paths: GamePaths, files: VersionFiles, onLog: (String) -> Unit): File? {
        if (generation(files) != 2) return null
        val jar = shim(paths)
        if (jar != null) {
            onLog("Minecraft antiguo: usando lwjglx (LWJGL 2 sobre LWJGL 3) · ${jar.name}")
            return jar
        }
        if (hasBuiltInShim(paths)) {
            onLog("Minecraft anterior a 1.13: LWJGL 2 resuelto por el stub de Android incluido.")
            return null
        }
        onLog(
            "Aviso: esta versión usa LWJGL 2 y no hay ninguna capa de compatibilidad: " +
                "falta lwjgl-glfw-stub.jar en ${paths.componentsDir.absolutePath}"
        )
        return null
    }
}
