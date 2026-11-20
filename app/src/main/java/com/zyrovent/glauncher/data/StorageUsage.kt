package com.zyrovent.glauncher.data

import android.os.StatFs
import java.io.File

/** Tramos de la barra de almacenamiento. */
enum class StorageKind { Versions, Runtimes, Assets, Logs, Other }

data class StorageSlice(
    val kind: StorageKind,
    val label: String,
    val bytes: Long
)

data class StorageReport(
    val totalBytes: Long,
    val freeBytes: Long,
    /** Lo que ocupa el launcher entero (juego + runtimes + componentes). */
    val appBytes: Long,
    val slices: List<StorageSlice>,
    val versions: List<Pair<String, Long>>,
    val runtimes: List<Pair<Int, Long>>
) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0L)
    val usedFraction: Float
        get() = if (totalBytes <= 0L) 0f else (usedBytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)
}

/**
 * Mide el espacio del dispositivo y lo que ocupa cada parte del launcher.
 * Todo se calcula bajo demanda en IO: recorrer carpetas de versiones no es
 * gratis y no debe bloquear la UI.
 */
object StorageUsage {

    fun report(paths: GamePaths): StorageReport {
        val anchor = paths.gameRoot.apply { mkdirs() }
        val stat = runCatching { StatFs(anchor.absolutePath) }.getOrNull()
        val total = stat?.totalBytes ?: 0L
        val free = stat?.availableBytes ?: 0L

        val versionDirs = directories(paths.instancesDir)
        val runtimeDirs = directories(paths.runtimesDir)

        val versions = versionDirs
            .map { dir -> dir.name to sizeOf(dir) }
            .sortedByDescending { it.second }

        val runtimes = runtimeDirs
            .map { dir -> (dir.name.toIntOrNull() ?: 0) to sizeOf(dir) }
            .filter { it.first > 0 }
            .sortedBy { it.first }

        val versionsBytes = versions.sumOf { it.second }
        val runtimesBytes = runtimes.sumOf { it.second }
        val assetsBytes = sizeOf(paths.assetsDir)
        val logsBytes = sizeOf(paths.logsDir)
        val librariesBytes = sizeOf(paths.librariesDir)
        val componentsBytes = sizeOf(paths.componentsDir)

        val appBytes = versionsBytes + runtimesBytes + assetsBytes + logsBytes +
            librariesBytes + componentsBytes
        val other = (librariesBytes + componentsBytes).coerceAtLeast(0L)

        val slices = listOf(
            StorageSlice(StorageKind.Versions, "Versiones", versionsBytes),
            StorageSlice(StorageKind.Runtimes, "Runtimes", runtimesBytes),
            StorageSlice(StorageKind.Assets, "Assets", assetsBytes),
            StorageSlice(StorageKind.Logs, "Registros", logsBytes),
            StorageSlice(StorageKind.Other, "Otros", other)
        ).filter { it.bytes > 0L }

        return StorageReport(
            totalBytes = total,
            freeBytes = free,
            appBytes = appBytes,
            slices = slices,
            versions = versions,
            runtimes = runtimes
        )
    }

    /** Tarjetas de versión: las carpetas con client.jar o version.json. */
    fun versionDirs(paths: GamePaths): List<File> =
        directories(paths.instancesDir).filter { dir ->
            File(dir, "client.jar").exists() || File(dir, "version.json").exists()
        }

    private fun directories(dir: File): List<File> =
        runCatching { dir.listFiles()?.filter { it.isDirectory }.orEmpty() }.getOrDefault(emptyList())

    fun sizeOf(target: File?): Long {
        if (target == null || !target.exists()) return 0L
        if (target.isFile) return target.length()
        var total = 0L
        val stack = ArrayDeque<File>()
        stack.addLast(target)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            val children = runCatching { current.listFiles() }.getOrNull() ?: continue
            for (child in children) {
                if (child.isDirectory) stack.addLast(child) else total += child.length()
            }
        }
        return total
    }

    /** Tamaño legible: 512 MB, 1,4 GB… */
    fun format(bytes: Long): String {
        val gb = bytes / 1024.0 / 1024.0 / 1024.0
        val mb = bytes / 1024.0 / 1024.0
        return when {
            bytes <= 0L -> "0 B"
            gb >= 1.0 -> String.format(java.util.Locale.US, "%.1f GB", gb)
            mb >= 1.0 -> String.format(java.util.Locale.US, "%.0f MB", mb)
            bytes >= 1024 -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}
