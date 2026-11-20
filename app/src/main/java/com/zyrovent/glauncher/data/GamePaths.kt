package com.zyrovent.glauncher.data

import android.content.Context
import java.io.File

/**
 * Rutas de almacenamiento del launcher. Usa el directorio externo propio de la app
 * (sin permisos) y, si no está disponible, filesDir.
 */
class GamePaths(context: Context) {

    /** Contexto de la app; también lo usan los perfiles de hardware (DeviceTuning). */
    val appContext = context.applicationContext

    val appLibDir: File = File(appContext.applicationInfo.nativeLibraryDir)

    val gameRoot: File =
        (appContext.getExternalFilesDir(null) ?: appContext.filesDir).resolve("glaucher")

    val instancesDir: File = gameRoot.resolve("instances")
    val assetsDir: File = gameRoot.resolve("assets")
    val assetIndexesDir: File = assetsDir.resolve("indexes")
    val assetObjectsDir: File = assetsDir.resolve("objects")
    val librariesDir: File = gameRoot.resolve("libraries")
    val logsDir: File = gameRoot.resolve("logs")

    /** Un motor Java por versión mayor: runtimes/8, runtimes/17... */
    val runtimesDir: File = appContext.filesDir.resolve("runtimes")
    val componentsDir: File = appContext.filesDir.resolve("components")
    val nativesDir: File = appContext.filesDir.resolve("natives")

    fun instanceDir(versionId: String): File = instancesDir.resolve(versionId)

    fun clientJar(versionId: String): File = instanceDir(versionId).resolve("client.jar")

    fun versionJson(versionId: String): File = instanceDir(versionId).resolve("version.json")

    fun assetIndexJson(indexId: String): File = assetIndexesDir.resolve("$indexId.json")

    fun runtimeDir(major: Int): File = runtimesDir.resolve(major.toString())

    fun runtimeJava(major: Int): File = runtimeDir(major).resolve("bin/java")

    fun hasRuntime(major: Int): Boolean = runtimeJava(major).exists()

    fun installedRuntimes(): List<Int> = runtimesDir.listFiles()
        ?.filter { it.isDirectory && File(it, "bin/java").exists() }
        ?.mapNotNull { it.name.toIntOrNull() }
        ?.sorted()
        ?: emptyList()

    /** Migración: en versiones anteriores había un único filesDir/runtime. */
    fun migrateLegacyRuntime() {
        val legacy = appContext.filesDir.resolve("runtime")
        if (File(legacy, "bin/java").exists()) {
            val target = runtimeDir(17)
            if (!target.exists()) {
                target.parentFile?.mkdirs()
                legacy.renameTo(target)
            }
        }
    }

    fun ensureDirectories() {
        listOf(instancesDir, assetsDir, assetIndexesDir, assetObjectsDir, librariesDir, logsDir, runtimesDir, componentsDir).forEach { it.mkdirs() }
    }
}
