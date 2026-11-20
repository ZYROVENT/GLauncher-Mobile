package com.zyrovent.glauncher.launch

import com.zyrovent.glauncher.data.Account
import com.zyrovent.glauncher.data.GamePaths
import com.zyrovent.glauncher.data.LauncherSettings
import com.zyrovent.glauncher.data.MojangRepository
import com.zyrovent.glauncher.data.RuntimeManager
import com.zyrovent.glauncher.data.VersionSummary
import java.io.File

enum class LaunchStage(val label: String) {
    ACCOUNT("Comprobando perfil"),
    VERSION("Resolviendo versión"),
    FILES("Verificando archivos del juego"),
    DOWNLOAD("Descargando cliente"),
    RUNTIME("Comprobando motor Java"),
    LAUNCH("Iniciando juego")
}

sealed interface LaunchResult {
    /**
     * Proceso Java arrancado; [process] es null cuando el JVM corre dentro de la
     * propia app (modo Android 10+) y la salida llega por el registro.
     */
    data class Started(val process: Process?) : LaunchResult

    /** No se pudo continuar; [detail] explica qué falta y cómo resolverlo. */
    data class Blocked(val title: String, val detail: String) : LaunchResult
}

/**
 * Orquesta el arranque: perfil -> metadatos -> archivos verificados -> motor Java ->
 * proceso. La descarga, la verificación SHA-1 y el arranque del proceso son reales;
 * que el cliente llegue a dibujar depende del runtime y del port de LWJGL que se
 * importen, y cualquier fallo de esos aparece en el registro.
 */
class LaunchPipeline(
    private val paths: GamePaths,
    private val repository: MojangRepository,
    private val installer: GameInstaller,
    private val runtimeManager: RuntimeManager,
    private val gameProcess: GameProcess,
    private val cacheDir: File
) {

    suspend fun run(
        account: Account?,
        versionId: String,
        settings: LauncherSettings,
        onStage: (LaunchStage) -> Unit,
        onLog: (String) -> Unit,
        onProgress: (Float) -> Unit
    ): LaunchResult {
        onStage(LaunchStage.ACCOUNT)
        onLog("GLauncher 1.0 · Minecraft $versionId")
        if (account == null) {
            onLog("No hay ningún perfil seleccionado.")
            return LaunchResult.Blocked(
                "Falta un perfil",
                "Crea un perfil offline en la pestaña Cuentas y vuelve a intentarlo."
            )
        }
        onLog("Perfil: ${account.username} (${account.type.label})")
        onLog("Memoria asignada: ${settings.ramMb} MB · Renderer: ${settings.renderer}")
        onLog("Perfil del dispositivo: ${DeviceTuning.summary(paths.appContext, settings.ramMb)}")
        onProgress(0.05f)

        onStage(LaunchStage.VERSION)
        val targetVersion = versionId.ifBlank { LauncherSettings.DEFAULT_VERSION }
        val localVersionJson = paths.versionJson(targetVersion)
        val cachedContent = if (localVersionJson.exists() && localVersionJson.length() > 0) {
            runCatching { localVersionJson.readText() }.getOrNull()
        } else null

        val files = try {
            val summary = repository.version(targetVersion)
            repository.files(summary, cachedContent)
        } catch (error: Exception) {
            // Si falla la red pero tenemos el version.json local, podemos intentar construir VersionFiles con él
            if (!cachedContent.isNullOrBlank()) {
                try {
                    val summary = VersionSummary(id = targetVersion, type = "release", releaseTime = "", metadataUrl = "")
                    repository.files(summary, cachedContent)
                } catch (_: Exception) {
                    onLog("No se pudieron leer los metadatos: ${error.message}")
                    return LaunchResult.Blocked(
                        "Sin conexión con Mojang",
                        "No se pudo descargar la información de $versionId. Revisa tu conexión e inténtalo otra vez.\n\n${error.message.orEmpty()}"
                    )
                }
            } else {
                onLog("No se pudieron leer los metadatos: ${error.message}")
                return LaunchResult.Blocked(
                    "Sin conexión con Mojang",
                    "No se pudo descargar la información de $versionId. Revisa tu conexión e inténtalo otra vez.\n\n${error.message.orEmpty()}"
                )
            }
        }
        onLog("Metadatos de ${files.id} listos · client.jar ${formatMb(files.clientSize)}")
        onProgress(0.15f)

        onStage(LaunchStage.FILES)
        val installed = try {
            installer.isInstalled(files)
        } catch (error: Exception) {
            onLog("Aviso: no se pudo verificar el jar (${error.message}).")
            false
        }

        if (!installed) {
            onStage(LaunchStage.DOWNLOAD)
            onLog("Faltan archivos del cliente, descargando...")
            try {
                installer.install(
                    files = files,
                    onLog = { line -> onLog("  $line") },
                    onProgress = { fraction -> onProgress(0.15f + fraction * 0.7f) }
                )
            } catch (error: Exception) {
                onLog("Descarga fallida: ${installer.installErrorMessage(error)}")
                return LaunchResult.Blocked("No se pudo descargar", installer.installErrorMessage(error))
            }
        } else {
            onLog("Archivos del juego verificados en disco.")
            // Los assets (sonidos, música, idiomas) se verifican SIEMPRE: el
            // chequeo de "instalado" solo mira el jar y las librerías, así que
            // una descarga interrumpida dejaba las versiones viejas sin audio
            // para siempre.
            onStage(LaunchStage.DOWNLOAD)
            try {
                installer.ensureAssets(files) { line -> onLog("  $line") }
            } catch (error: Exception) {
                onLog("Aviso: no se pudieron completar los assets (${error.message})")
            }
            if (files.metadataJson.isNotBlank()) {
                val vJson = paths.versionJson(files.id)
                if (!vJson.exists() || vJson.length() == 0L) {
                    runCatching { vJson.writeText(files.metadataJson) }
                }
            }
            onProgress(0.85f)
        }

        onStage(LaunchStage.RUNTIME)
        val declarado = supportedMajor(files.javaMajor ?: fallbackJavaMajor(files.id))
        val major = pickRuntime(declarado, onLog)
        // Mods instalados en la instancia: cambian el metaspace, el consejo de
        // RAM y el recorte de distancia de simulación.
        val mods = countMods(paths.instanceDir(files.id))
        val engineProfile = DeviceTuning.profile(paths.appContext, settings.ramMb, mods)
        onLog("Perfil del motor: ${DeviceTuning.summary(paths.appContext, settings.ramMb, mods)}")
        DeviceTuning.heapAdvice(engineProfile)?.let { advice -> onLog("Aviso: $advice") }
        val runtime = runtimeManager.status(major)
        // Un solo motor instalado puede servir para varias versiones: si pedía 21
        // pero solo hay 17 (o 25), se avisa antes de fallar feo.
        if (!runtime.installed) {
            onLog("Falta el motor Java $major (${paths.runtimeDir(major).absolutePath})")
            val disponibles = runtimeManager.installed()
            val usables = disponibles.filter { it >= major }.sorted()
            onLog("Motores instalados: ${disponibles.joinToString(", ").ifBlank { "ninguno" }}")
            return LaunchResult.Blocked(
                "Falta Java $major",
                "Los archivos de Minecraft ${files.id} ya están descargados, pero esta versión " +
                    "necesita Java $major.\n\n" +
                    "Ve a Ajustes → Motor Java y descárgalo (presets para 8, 17, 21 y 25)." +
                    if (usables.isNotEmpty()) {
                        "\n\nTienes Java ${usables.joinToString(", ")}, pero Minecraft exige $major."
                    } else ""
            )
        }
        onLog("Motor Java $major: ${runtime.javaPath} (${runtime.sizeMb} MB)")
        val lwjgl = LwjglCompat.generation(files)
        onLog("Cliente sobre LWJGL $lwjgl · assets ${files.assetIndexId ?: "propios"}")
        val lwjglShim = LwjglCompat.prepare(paths, files, onLog)

        onProgress(0.95f)
        onStage(LaunchStage.LAUNCH)
        val nativesDir = NativeLibs.prepare(paths, settings.renderer)
        onLog("Natives: ${nativesDir.absolutePath}")
        onLog("Renderer: ${NativeLibs.rendererLibrary(settings.renderer)}")
        val command = JvmCommandBuilder.build(
            account = account,
            settings = settings,
            paths = paths,
            versionId = files.id,
            assetIndex = files.assetIndexId,
            runtimeMajor = major,
            classpath = installer.classpath(files, lwjglShim),
            nativesDir = nativesDir,
            mods = mods
        )
        onLog("Classpath: ${installer.classpath(files, lwjglShim).size} ficheros")
        onLog("Orden de arranque:")
        onLog("  " + JvmCommandBuilder.describe(command))

        return try {
            val argv = listOf("java") + command.drop(1)
            val runtimeHome = paths.runtimeDir(major)
            // Se descubren las librerías reales del runtime: cada JRE las coloca
            // en carpetas distintas y no conviene hardcodear lib/jli y demás.
            val runtimeLibs = RuntimeLayout.libDirs(runtimeHome)
            onLog("Librerías del motor: ${RuntimeLayout.describe(runtimeHome)}")

            if (RuntimeLayout.jvm(runtimeHome) == null) {
                LaunchResult.Blocked(
                    "No se pudo iniciar Java",
                    "No se encontró libjvm.so en el motor $major.\n\n" +
                        "Se revisó todo ${runtimeHome.absolutePath}\n" +
                        RuntimeLayout.tree(runtimeHome)
                )
            } else {
                // El puente EGL necesita la Surface ya creada antes de que el JVM
                // inicialice OpenGL, así que se espera (acotado) a que esté lista.
                if (!GameWindow.ready) {
                    onLog("Esperando la ventana del juego...")
                    val limit = System.currentTimeMillis() + 6000
                    while (!GameWindow.ready && System.currentTimeMillis() < limit) {
                        kotlinx.coroutines.delay(50)
                    }
                }
                GameWindow.lastError?.let { message -> onLog(message) }
                onLog(
                    if (GameWindow.ready) "Ventana del juego lista."
                    else "Aviso: sin ventana enlazada; el renderer puede no arrancar."
                )
                // Con poco heap, el cliente pedía distancia 12 y llegaba a 1,6 GB
                // de memoria nativa: se recortan las opciones antes de arrancar.
                GameOptionsTuning.tune(
                    instanceDir = paths.instanceDir(files.id),
                    heapMb = settings.ramMb,
                    tier = engineProfile.tier,
                    mods = mods
                ) { line -> onLog(line) }
                val preloadDirs = (listOf(nativesDir, paths.appLibDir) + runtimeLibs)
                    .distinctBy { it.absolutePath }
                val env = gameProcess.environment(major) +
                    mapOf("JAVA_HOME" to runtimeHome.absolutePath)
                // Una línea con lo que de verdad se le pasó a gl4es: si algo no
                // cuadra con lo que dice Ajustes, se ve aquí sin adivinar.
                onLog(
                    "Motor gráfico: " + env.filterKeys {
                        it.startsWith("LIBGL_") || it.startsWith("FORCE_") ||
                            it.startsWith("POJAV_") || it.startsWith("MESA_")
                    }.entries.joinToString(" ") { "${it.key}=${it.value}" }
                )
                val started = InProcessLaunch.start(
                    runtimeHome = runtimeHome,
                    libDirs = preloadDirs,
                    workDir = paths.instanceDir(files.id),
                    argv = argv,
                    onLine = { line -> onLog(line) },
                    env = env
                )
                if (!started) {
                    LaunchResult.Blocked(
                        "No se pudo iniciar Java",
                        "El motor $major no arrancó o ya hay un JVM en marcha.\n\n" +
                            runtimeHome.absolutePath
                    )
                } else {
                    onLog("JVM arrancado en el proceso de la app (sin exec, compatible con Android 10+).")
                    onProgress(1f)
                    LaunchResult.Started(null)
                }
            }
        } catch (error: Exception) {
            onLog("No se pudo iniciar el proceso: ${error.message}")
            LaunchResult.Blocked(
                "No se pudo iniciar Java",
                "El runtime se encontró, pero el proceso no arrancó.\n\n${error.message.orEmpty()}\n\n" +
                    "Comprueba que el runtime sea ARM64 y que bin/java tenga permisos de ejecución."
            )
        }
    }

    private fun formatMb(bytes: Long): String = "%.1f MB".format(bytes / (1024.0 * 1024.0))

    /** Jars en `mods/`: cada uno es un mod (o un pack de recursos metido ahí). */
    private fun countMods(instanceDir: File): Int {
        val dir = File(instanceDir, "mods")
        if (!dir.isDirectory) return 0
        return dir.listFiles()?.count { it.isFile && it.name.endsWith(".jar", ignoreCase = true) } ?: 0
    }

    /**
     * Java del motor. Las versiones que declaran 21 (1.20.5 en adelante, 1.21,
     * 26.x) corren igual con 25: el cliente nuevo solo pide 21 como mínimo, y
     * OpenJDK 25 compila mejor ese código (más optimizaciones del JIT) y trae
     * un G1 con menos trabajo por ciclo. Si el 25 no está instalado se usa el
     * que pida la versión, sin obligar a descargar nada para jugar.
     */
    private fun pickRuntime(declarado: Int, onLog: (String) -> Unit): Int {
        if (declarado < 21) return declarado
        val instalados = runtimeManager.installed()
        if (25 in instalados) {
            if (declarado != 25) {
                onLog("Java 25 instalado: se usa para este cliente (declara Java $declarado).")
            }
            return 25
        }
        if (declarado !in instalados && 21 in instalados) {
            onLog("Sin Java 25; se arranca con Java 21 (que es lo que pide el cliente).")
            return 21
        }
        return declarado
    }

    /**
     * Motores soportados: 8, 17, 21 y 25. Ojo con los cortes: 1.17 a 1.20.4
     * declara Java 17 y debe quedarse en 17; solo 1.20.5 en adelante usa 21.
     */
    private fun supportedMajor(requested: Int): Int = when {
        requested <= 8 -> 8
        requested <= 17 -> 17
        requested <= 21 -> 21
        else -> 25
    }

    /** "1.16.5" -> 8, "1.19" -> 17, "1.21" -> 21, cuando la metadata no lo declara. */
    private fun fallbackJavaMajor(versionId: String): Int {
        val parts = versionId.substringBefore('-').trim().split('.')
        // Versiones nuevas sin el "1." delante (26.x): Java 21 o superior.
        if (parts.firstOrNull() != "1") return 21
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: return 21
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        return when {
            minor >= 21 -> 21
            minor == 20 && patch >= 5 -> 21  // 1.20.5+ exige Java 21
            minor >= 17 -> 17
            else -> 8
        }
    }
}
