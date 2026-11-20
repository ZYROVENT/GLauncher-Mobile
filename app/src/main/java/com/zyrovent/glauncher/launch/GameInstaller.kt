package com.zyrovent.glauncher.launch

import com.zyrovent.glauncher.data.Downloader
import com.zyrovent.glauncher.data.GamePaths
import com.zyrovent.glauncher.data.LibraryArtifact
import com.zyrovent.glauncher.data.VersionFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Descarga y verifica los archivos del cliente de una versión. El jar del cliente
 * se valida por SHA-1 contra los metadatos oficiales antes de darlo por bueno.
 */
class GameInstaller(
    private val paths: GamePaths,
    private val downloader: Downloader = Downloader()
) {

    fun isInstalled(files: VersionFiles): Boolean {
        val jar = paths.clientJar(files.id)
        if (!jar.exists() || jar.length() != files.clientSize) return false
        if (!downloader.sha1(jar).equals(files.clientSha1, ignoreCase = true)) return false
        val vJson = paths.versionJson(files.id)
        if (!vJson.exists() || vJson.length() == 0L) return false
        val indexId = files.assetIndexId
        if (!indexId.isNullOrBlank()) {
            val idxFile = paths.assetIndexJson(indexId)
            if (!idxFile.exists() || idxFile.length() == 0L) return false
        }
        return files.libraries.all { library ->
            val file = paths.librariesDir.resolve(library.path)
            file.exists() && (library.size <= 0L || file.length() == library.size)
        }
    }

    /**
     * Classpath completo: stub de LWJGL/GLFW (para Android) + client.jar + todas
     * las librerías descargadas.
     *
     * [lwjglShim] (lwjglx) se pone el primero y, cuando está, se excluyen los jars
     * de LWJGL 2: son la API de escritorio que Android no puede cargar.
     */
    fun classpath(files: VersionFiles, lwjglShim: File? = null): List<String> {
        val stubJar = paths.componentsDir.resolve("lwjgl-glfw-stub.jar")
        val stub = if (stubJar.exists()) listOf(stubJar.absolutePath) else emptyList()
        // El stub pide net.java.openjdk.cacio.ctc.* en las versiones antiguas:
        // sin este jar el cliente 1.8.x muere con NoClassDefFoundError + SIGSEGV.
        val cacioJar = paths.componentsDir.resolve("cacio-androidnw-1.10-SNAPSHOT.jar")
        val cacio = if (cacioJar.exists()) listOf(cacioJar.absolutePath) else emptyList()
        val shim = if (lwjglShim != null && lwjglShim.exists()) listOf(lwjglShim.absolutePath) else emptyList()
        return shim + stub + cacio + listOf(paths.clientJar(files.id).absolutePath) +
            files.libraries.mapNotNull { library ->
                if (lwjglShim != null && LwjglCompat.replacedJars(library.path)) return@mapNotNull null
                val file = paths.librariesDir.resolve(library.path)
                file.takeIf { it.exists() }?.absolutePath
            }
    }

    suspend fun install(
        files: VersionFiles,
        onLog: (String) -> Unit,
        onProgress: (Float) -> Unit
    ) = withContext(Dispatchers.IO) {
        paths.ensureDirectories()
        val jar = paths.clientJar(files.id)

        onLog("Descargando client.jar de ${files.id} (${formatSize(files.clientSize)})")
        var lastReported = -1
        downloader.download(
            url = files.clientUrl,
            dest = jar,
            expectedSha1 = files.clientSha1.takeIf { it.isNotBlank() },
            expectedSize = files.clientSize
        ) { done, total ->
            val percent = if (total > 0) ((done.toDouble() / total) * 55).toInt() else 0
            if (percent != lastReported) {
                lastReported = percent
                onProgress(percent / 100f)
            }
        }
        onLog("SHA-1 verificado: ${downloader.sha1(jar)}")

        // Librerías del classpath en paralelo (hasta 8 conexiones simultáneas para máxima velocidad)
        val libraries = files.libraries
        if (libraries.isEmpty()) {
            onLog("Esta versión no declara librerías externas.")
        } else {
            onLog("Descargando ${libraries.size} librerías en paralelo...")
            val semaphore = Semaphore(8)
            val done = AtomicInteger(0)
            val failed = AtomicInteger(0)

            libraries.map { library ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val destination = paths.librariesDir.resolve(library.path)
                        val fresh = destination.exists() &&
                            (library.size <= 0L || destination.length() == library.size)
                        if (!fresh) {
                            try {
                                downloader.download(
                                    url = library.url,
                                    dest = destination,
                                    expectedSha1 = library.sha1.takeIf { it.isNotBlank() },
                                    expectedSize = library.size
                                )
                            } catch (error: Exception) {
                                failed.incrementAndGet()
                                onLog("  no se pudo ${library.path}: ${error.message}")
                            }
                        }
                        val count = done.incrementAndGet()
                        onProgress(0.55f + (0.45f * count / libraries.size))
                    }
                }
            }.awaitAll()

            val failedCount = failed.get()
            onLog("Librerías listas: ${libraries.size - failedCount}/${libraries.size}")
            if (failedCount > 0) onLog("Aviso: faltan $failedCount librerías, el juego puede no arrancar.")
        }

        if (files.metadataJson.isNotBlank()) {
            paths.versionJson(files.id).writeText(files.metadataJson)
            onLog("Guardado metadatos version.json para ${files.id}")
        }

        // Descarga del Asset Index y objetos de assets en paralelo
        downloadAssets(files, onLog)

        onProgress(1f)
    }

    /**
     * Verifica y completa los assets de una versión ya instalada.
     *
     * El instalador completo solo se ejecuta la primera vez; esto se llama en
     * cada arranque para que los sonidos, la música y los idiomas no se queden a
     * medias si una descarga anterior se cortó (era la causa de que Minecraft
     * viejo arrancara en silencio).
     */
    suspend fun ensureAssets(files: VersionFiles, onLog: (String) -> Unit) =
        withContext(Dispatchers.IO) { downloadAssets(files, onLog) }

    private suspend fun downloadAssets(files: VersionFiles, onLog: (String) -> Unit) = withContext(Dispatchers.IO) {
        val indexId = files.assetIndexId ?: return@withContext
        val indexUrl = files.assetIndexUrl
        val indexFile = paths.assetIndexJson(indexId)

        if (indexUrl.isNullOrBlank()) {
            onLog("Aviso: no hay URL para assetIndex $indexId")
            return@withContext
        }

        val rawIndex = try {
            if (!indexFile.exists() || indexFile.length() == 0L) {
                onLog("Descargando assetIndex $indexId.json...")
                downloader.download(
                    url = indexUrl,
                    dest = indexFile,
                    expectedSha1 = files.assetIndexSha1.takeIf { !it.isNullOrBlank() }
                )
            }
            indexFile.readText()
        } catch (e: Exception) {
            onLog("Aviso: no se pudo descargar index $indexId.json: ${e.message}")
            return@withContext
        }

        // Descargar objetos esenciales del assetIndex (sonidos, idiomas, etc.)
        try {
            val root = JSONObject(rawIndex)
            val objects = root.optJSONObject("objects") ?: return@withContext
            val keys = objects.keys()
            val pendingDownloads = mutableListOf<Pair<String, Long>>()
            var skippedCount = 0

            while (keys.hasNext()) {
                val key = keys.next()
                val obj = objects.optJSONObject(key) ?: continue
                val hash = obj.optString("hash").trim()
                val size = obj.optLong("size", -1L)
                if (hash.length < 2) continue

                val prefix = hash.substring(0, 2)
                val objectDest = paths.assetObjectsDir.resolve(prefix).resolve(hash)

                if (objectDest.exists() && (size <= 0L || objectDest.length() == size)) {
                    skippedCount++
                    continue
                }
                pendingDownloads.add(hash to size)
            }

            if (pendingDownloads.isNotEmpty()) {
                onLog("Descargando ${pendingDownloads.size} assets en paralelo (8 hilos)...")
                val semaphore = Semaphore(8)
                val downloadedCount = AtomicInteger(0)

                pendingDownloads.map { (hash, size) ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            val prefix = hash.substring(0, 2)
                            val objectDest = paths.assetObjectsDir.resolve(prefix).resolve(hash)
                            val objUrl = "https://resources.download.minecraft.net/$prefix/$hash"
                            try {
                                downloader.download(
                                    url = objUrl,
                                    dest = objectDest,
                                    expectedSha1 = hash,
                                    expectedSize = size
                                )
                                downloadedCount.incrementAndGet()
                            } catch (_: Exception) {
                                // Continuar si falla un recurso opcional
                            }
                        }
                    }
                }.awaitAll()
                onLog("Assets verificados: ${downloadedCount.get()} descargados, $skippedCount ya en caché.")
            } else {
                onLog("Assets verificados: todos los $skippedCount objetos ya están en caché.")
            }
        } catch (e: Exception) {
            onLog("Aviso al procesar objetos de assets: ${e.message}")
        }

        // Minecraft viejo (1.6 y anteriores) no lee el index: busca los assets
        // por su nombre en assets/virtual/legacy. Sin esta carpeta el juego
        // arranca sin sonidos, idiomas ni fuentes.
        if (indexId == "legacy" || indexId == "pre-1.6") {
            materializeLegacy(indexId, onLog)
        }
    }

    /** Copia los objetos del index antiguo a assets/virtual/legacy con su nombre real. */
    private fun materializeLegacy(indexId: String, onLog: (String) -> Unit) {
        val indexFile = paths.assetIndexJson(indexId)
        if (!indexFile.exists()) return
        val target = paths.assetsDir.resolve("virtual").resolve("legacy")
        if (target.exists() && (target.listFiles()?.isNotEmpty() == true)) return
        runCatching {
            val objects = JSONObject(indexFile.readText()).optJSONObject("objects") ?: return
            var copied = 0
            val keys = objects.keys()
            while (keys.hasNext()) {
                val name = keys.next()
                val hash = objects.optJSONObject(name)?.optString("hash").orEmpty()
                if (hash.length < 2) continue
                val source = paths.assetObjectsDir.resolve(hash.substring(0, 2)).resolve(hash)
                if (!source.exists()) continue
                val destination = target.resolve(name)
                destination.parentFile?.mkdirs()
                source.copyTo(destination, overwrite = true)
                copied++
            }
            onLog("Assets virtuales de $indexId preparados: $copied ficheros.")
        }.onFailure {
            onLog("Aviso: no se pudieron preparar los assets virtuales: ${it.message}")
        }
    }

    fun sizeOnDisk(files: VersionFiles): Long = paths.clientJar(files.id).takeIf { it.exists() }?.length() ?: 0L

    fun installErrorMessage(error: Exception): String = when (error) {
        is IOException -> "No se pudo completar la descarga: ${error.message}"
        else -> "Error inesperado: ${error.message ?: error::class.java.simpleName}"
    }

    private fun formatSize(bytes: Long): String = when {
        bytes <= 0L -> "tamaño desconocido"
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    }
}
