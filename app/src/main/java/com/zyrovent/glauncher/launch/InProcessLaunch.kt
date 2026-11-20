package com.zyrovent.glauncher.launch

import android.os.ParcelFileDescriptor
import android.system.Os
import java.io.File

/**
 * Arranca el JVM de Minecraft dentro del propio proceso de la app.
 *
 * Android 10+ prohíbe ejecutar binarios del directorio privado de la app (W^X),
 * así que el modelo de proceso hijo con exec() deja de ser una opción a partir de
 * targetSdk 29.
 *
 * La primera versión llamaba a `JLI_Launch` de libjli, el lanzador oficial de
 * Java. En este dispositivo eso termina con el proceso asesinado (SIGKILL) justo
 * después de empezar, antes siquiera de crear la máquina virtual. Por eso ahora
 * se hace el camino corto: `JNI_CreateJavaVM` desde libjvm.so, en un hilo
 * nativo propio, y desde ahí se invoca `net.minecraft.client.main.Main.main()`.
 *
 * El juego comparte proceso con la app: al terminar, la salida vuelve por
 * [onExit] y la stdio se restaura para no dejar la aplicación sin consola.
 */
object InProcessLaunch {

    init {
        System.loadLibrary("glauncher")
    }

    /** true mientras el JVM está en marcha (solo puede haber uno por proceso). */
    @Volatile
    var running: Boolean = false
        private set

    /** Código de salida del último arranque, para no perder el aviso si llega tarde. */
    @Volatile
    var lastExitCode: Int? = null
        private set

    /** Se registra desde la UI para reaccionar al fin del juego. */
    @Volatile
    var onExit: ((Int) -> Unit)? = null

    /** Solo puede existir un JVM por proceso: no se reintenta. */
    @Volatile
    private var jvmCreated: Boolean = false

    private external fun preloadLibs(dirs: Array<String>): Int
    private external fun redirectStdio(writeFd: Int): Boolean
    private external fun restoreStdio()
    private external fun chdirTo(path: String): Boolean
    private external fun createAndRun(
        jvmPath: String,
        options: Array<String>,
        mainClass: String?,
        args: Array<String>
    ): Int

    private external fun probeJvm(jvmPath: String, options: Array<String>): Int

    /**
     * Crea la máquina virtual directamente con `JNI_CreateJavaVM`, sin libjli.
     * Devuelve el código de JNI (0 = VM creada). Solo para diagnóstico.
     */
    fun probe(jvmPath: File, options: List<String>): Int =
        probeJvm(jvmPath.absolutePath, options.toTypedArray())

    /**
     * @param runtimeHome carpeta del runtime (contiene lib/ y bin/).
     * @param libDirs directorios a precargar antes de arrancar.
     * @param workDir directorio de trabajo (la instancia del juego).
     * @param argv argv del cliente; argv[0] debe ser "java".
     */
    fun start(
        runtimeHome: File,
        libDirs: List<File>,
        workDir: File,
        argv: List<String>,
        onLine: (String) -> Unit,
        env: Map<String, String> = emptyMap(),
        onStarting: (() -> Unit)? = null
    ): Boolean {
        if (running) return false
        if (jvmCreated) {
            onLine("Ya se creó un JVM en este proceso; reinicia la app para volver a jugar.")
            return false
        }

        val jvmLib = RuntimeLayout.jvm(runtimeHome)
        if (jvmLib == null) {
            onLine("Falta libjvm.so dentro de ${runtimeHome.absolutePath}")
            return false
        }

        running = true
        jvmCreated = true
        lastExitCode = null

        env.forEach { (key, value) -> runCatching { Os.setenv(key, value, true) } }

        val loaded = preloadLibs(libDirs.map { it.absolutePath }.toTypedArray())
        onLine("Librerías del runtime precargadas: $loaded")
        if (!chdirTo(workDir.absolutePath)) {
            onLine("No se pudo trabajar en ${workDir.absolutePath}")
        }

        val pipe = ParcelFileDescriptor.createPipe()
        val reader = pipe[0]
        val writer = pipe[1]
        if (!redirectStdio(writer.fd)) {
            running = false
            return false
        }

        // La salida también se vuelca a disco: si el proceso muere, el archivo
        // conserva lo último que escribió la JVM.
        val logsDir = File(workDir, "logs").apply { mkdirs() }
        val logFile = File(logsDir, "glauncher-jvm.log")
        val latestLogFile = File(logsDir, "latest.log")
        runCatching {
            logFile.writeText("")
            latestLogFile.writeText("")
        }

        val outputThread = Thread(
            {
                runCatching {
                    val sink = logFile.outputStream().bufferedWriter()
                    val latestSink = latestLogFile.outputStream().bufferedWriter()
                    // Volcar disco en CADA línea son dos escrituras y dos
                    // flush por línea; el cliente escupe cientos por segundo y
                    // eso se paga en el hilo del juego. Se escribe siempre y se
                    // vuelca como mucho cada 200 ms, salvo en las líneas que
                    // importan si algo revienta (ahí se vuelca al instante).
                    var ultimoVolcado = 0L
                    ParcelFileDescriptor.AutoCloseInputStream(reader)
                        .bufferedReader()
                        .forEachLine { line ->
                            runCatching {
                                sink.write(line); sink.newLine()
                                latestSink.write(line); latestSink.newLine()
                                val ahora = System.currentTimeMillis()
                                val urgente = line.contains("FATAL") ||
                                    line.contains("Exception") ||
                                    line.contains("Caused by") ||
                                    line.contains("ERROR") ||
                                    line.contains("# A fatal error")
                                if (urgente || ahora - ultimoVolcado >= 200) {
                                    ultimoVolcado = ahora
                                    sink.flush()
                                    latestSink.flush()
                                }
                            }
                            runCatching { onLine(line) }
                        }
                    runCatching { sink.flush() }
                    runCatching { latestSink.flush() }
                    runCatching { sink.close() }
                    runCatching { latestSink.close() }
                }
                runCatching { reader.close() }
            },
            "glauncher-stdio"
        ).apply {
            isDaemon = true
            start()
        }
        runCatching { writer.close() }

        val (parsedOptions, mainClass, gameArgs) = splitCommand(argv, runtimeHome)
        val tempDir = File(env["TMPDIR"] ?: File(workDir, "tmp").absolutePath).apply { mkdirs() }
        // JNA y el Narrador de Minecraft escriben en java.io.tmpdir; sin esto
        // se quedan en /tmp, que no existe en Android.
        val vmOptions = if (parsedOptions.any { it.startsWith("-Djava.io.tmpdir=") }) {
            parsedOptions
        } else {
            parsedOptions + "-Djava.io.tmpdir=${tempDir.absolutePath}"
        }
        onLine("Máquina virtual: $jvmLib")
        onLine("Clase principal: ${mainClass ?: "(ninguna)"}")

        onStarting?.invoke()
        Thread(
            null,
            Runnable {
                onLine("=== INICIANDO HOTSPOT (JNI_CreateJavaVM) ===")
                val code = runCatching {
                    createAndRun(
                        jvmLib.absolutePath,
                        vmOptions.toTypedArray(),
                        mainClass,
                        gameArgs.toTypedArray()
                    )
                }.getOrElse { error ->
                    onLine("ERROR CRÍTICO: No se pudo arrancar el JVM: ${error.message}")
                    -1
                }
                restoreStdio()
                lastExitCode = code
                running = false
                onLine(if (code == 0) "=== EL JUEGO TERMINÓ ===" else "=== EL JUEGO TERMINÓ CON CÓDIGO $code ===")
                onExit?.invoke(code)
            },
            "glauncher-jvm",
            // HotSpot necesita bastante pila para inicializarse.
            16L * 1024 * 1024
        ).start()
        return true
    }

    /**
     * Convierte la línea de comandos de Java en las tres partes que pide JNI:
     * opciones de la VM, clase principal y argumentos del programa.
     */
    private fun splitCommand(
        argv: List<String>,
        runtimeHome: File
    ): Triple<List<String>, String?, List<String>> {
        val options = mutableListOf<String>()
        var classpath: String? = null
        var index = 1 // argv[0] es la ruta del "bin/java"
        while (index < argv.size) {
            val arg = argv[index]
            when {
                arg == "-cp" || arg == "-classpath" || arg == "--class-path" -> {
                    classpath = argv.getOrNull(index + 1)
                    index += 2
                }

                arg == "-jar" -> {
                    classpath = argv.getOrNull(index + 1)
                    index += 2
                }

                arg.startsWith("-") -> {
                    options += arg
                    index++
                }

                else -> break
            }
        }
        if (classpath != null) options += "-Djava.class.path=$classpath"
        if (options.none { it.startsWith("-Djava.home=") }) {
            options += "-Djava.home=${runtimeHome.absolutePath}"
        }
        val mainClass = argv.getOrNull(index)
        val gameArgs = if (index < argv.lastIndex) argv.subList(index + 1, argv.size) else emptyList()
        return Triple(options, mainClass, gameArgs)
    }
}
