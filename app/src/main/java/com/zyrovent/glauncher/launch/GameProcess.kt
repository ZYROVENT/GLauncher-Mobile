package com.zyrovent.glauncher.launch

import android.util.Log
import com.zyrovent.glauncher.data.GamePaths
import java.io.File

/** Arranca el proceso Java del cliente y prepara el entorno que necesita un JVM en
 *  Android: HOME, TMPDIR, directorio de trabajo de la instancia y, sobre todo,
 *  LD_LIBRARY_PATH (bionic ignora el RPATH del binario, así que las librerías del
 *  runtime se declaran aquí). */
class GameProcess(
    private val paths: GamePaths,
    /** libtagoff.so: se precarga en el JVM para apagar el tagging de punteros. */
    val preloadLib: String? = null
) {

    /** Variables de entorno comunes para la prueba del motor y el juego. */
    fun environment(major: Int): Map<String, String> {
        // Las carpetas salen del runtime instalado (las que de verdad tienen .so).
        val runtimeLibs = RuntimeLayout.libDirs(paths.runtimeDir(major))
            .map { it.absolutePath }

        val inherited = System.getenv("LD_LIBRARY_PATH")
        val appLibs = listOf(paths.appLibDir.absolutePath)
        val libraryPath = (appLibs + runtimeLibs + listOfNotNull(inherited)).joinToString(":")

        val preload = preloadLib?.takeIf { File(it).exists() }
        val inheritedPreload = System.getenv("LD_PRELOAD")
        val preloadPath = listOfNotNull(preload, inheritedPreload?.takeIf { it.isNotBlank() })
            .joinToString(":")

        // OJO: aquí había banderas de gl4es (LIBGL_SHRINK / LIBGL_BATCH) por
        // perfil de GPU. Se quitaron porque LIBGL_SHRINK hace que gl4es
        // reescale texturas grandes y su ruta de conversión de píxeles peta con
        // un puntero nulo al recargar los recursos del mundo: SIGSEGV en
        // libgl4es_114.so justo en «Reloading ResourceManager: Default».
        // El rendimiento se baja, aparte, reduciendo la resolución de render.
        val (gpuProfile, _) = GpuProfiles.resolve(paths.appContext)
        Log.i("GLauncher", "Perfil GPU detectado: $gpuProfile")

        return buildMap {
            put("LD_LIBRARY_PATH", libraryPath)
            if (preloadPath.isNotEmpty()) put("LD_PRELOAD", preloadPath)
            put("HOME", paths.gameRoot.absolutePath)
            put("TMPDIR", paths.gameRoot.resolve("tmp").apply { mkdirs() }.absolutePath)
            put("LIBGL_ES", "2")
            put("LIBGL_UPLOAD", "1")
            // gl4es: el upload inmediato evita reenviar buffers cada frame y el
            // "nobanner" quita el volcado de información en cada arranque.
            put("LIBGL_NOBANNER", "1")
            // Aritmética rápida en los shaders que gl4es genera al convertir el
            // pipeline fijo: en la Mali-G51 el cálculo de luz por vértice es de
            // lo más caro del fotograma y con esto baja bastante. Solo cambia
            // la precisión (imperceptible en una pantalla de 6"), no el resultado.
            put("LIBGL_FASTMATH", "1")
            // gl4es, por defecto, guarda una copia en RAM de cada textura que
            // sube a la GPU (para poder responder a glGetTexImage). Son cientos
            // de MB del atlas del terreno copiados dos veces y una copia por
            // textura al cargar el mundo: se salta. El cliente 1.21 no lee
            // texturas de vuelta, solo las sube.
            put("LIBGL_SKIPTEXCOPIES", "1")
            // El framebuffer intermedio de gl4es se pide sin canal alfa: es un
            // 25 % menos de ancho de banda por fotograma en cada escritura de
            // color, y aquí la pantalla es opaca (ver isOpaque en GameSurface).
            put("LIBGL_FBONOALPHA", "1")
            // glGetError en gl4es obliga a sincronizar con la GPU para saber si
            // algo falló. Minecraft lo llama muchísimo; con esto devuelve 0 sin
            // consultar nada. Se pierden los diagnósticos de error de OpenGL,
            // no el dibujado.
            put("LIBGL_NOERROR", "1")
            put("LIBGL_NOTEST", "1")
            put("MESA_GL_VERSION_OVERRIDE", "4.0")
            // El puente EGL de Pojav lee estas variables: sin POJAV_RENDERER coge
            // la rama GL4ES y con POJAV_NATIVEDIR localiza sus .so.
            put("POJAV_RENDERER", "opengles")
            put("POJAV_NATIVEDIR", paths.appLibDir.absolutePath)
            put("FORCE_VSYNC", "false")
            // gl4es imprime una línea cada vez que el juego llama a algo que no
            // puede traducir (cientos por segundo en algunos menús). Escribir en
            // stdout desde el hilo de render cuesta frames; se calla.
            put("LIBGL_SILENTSTUB", "1")
            // En Mali el `highp` del shader de fragmentos se ejecuta igual que
            // el `mediump` pero gasta más: obligando mediump en los shaders que
            // genera gl4es, la Mali-G51 respira. Solo se aplica al perfil Mali
            // (ver GpuProfiles); en Adreno/PowerVR se queda el de por defecto.
            if (gpuProfile == "mali") put("LIBGL_NOHIGHP", "1")
        }
    }

    fun start(command: List<String>, instanceDir: File, major: Int): Process {
        require(command.isNotEmpty()) { "La orden de arranque está vacía" }
        instanceDir.mkdirs()
        val builder = ProcessBuilder(command)
            .directory(instanceDir)
            .redirectErrorStream(true)

        environment(major).forEach { (key, value) -> builder.environment()[key] = value }
        return builder.start()
    }
}
