package com.zyrovent.glauncher.launch

import java.io.File

/**
 * Descubre cómo está montado un runtime Java instalado recorriendo sus `.so`.
 *
 * Los JRE que se instalan en Android no siempre vienen con la misma estructura:
 * unos traen `lib/libjli.so`, otros `lib/jli/libjli.so`, y algunos añaden
 * subcarpetas propias. En vez de adivinar rutas, se busca el archivo por nombre
 * y se usan los directorios reales para el precargado y para
 * `-Djava.library.path`.
 */
object RuntimeLayout {

    /** Todos los `.so` del runtime, recorriendo subcarpetas. */
    fun sharedLibs(home: File): List<File> =
        walk(home).filter { it.isFile && it.name.endsWith(".so") }

    /** Directorios que contienen `.so`: exactamente lo que hay que precargar. */
    fun libDirs(home: File): List<File> = sharedLibs(home)
        .mapNotNull { it.parentFile }
        .distinctBy { it.absolutePath }
        // Los directorios más generales primero: lib antes que lib/server.
        .sortedBy { it.absolutePath.length }

    /** Busca una librería por nombre exacto en todo el árbol del runtime. */
    fun find(home: File, name: String): File? =
        sharedLibs(home).firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** Envoltorio que arranca el JVM (JLI_Launch). */
    fun jli(home: File): File? = find(home, "libjli.so")

    /** El propio motor de HotSpot. */
    fun jvm(home: File): File? = find(home, "libjvm.so")

    /** Nombres de las librerías encontradas, para poder enseñarlas en el log. */
    fun describe(home: File): String {
        val libs = sharedLibs(home)
        if (libs.isEmpty()) return "sin librerías .so"
        return libs.size.toString() + " librerías en " +
            libDirs(home).joinToString(", ") { it.name }
    }

    /** Resumen de carpetas y cuántas .so tiene cada una, para mensajes y logs. */
    fun tree(home: File, maxDirs: Int = 10): String {
        val libs = sharedLibs(home)
        if (libs.isEmpty()) return "No hay archivos .so dentro."
        return libs
            .groupBy { it.parentFile }
            .toList()
            .sortedBy { it.first.absolutePath.length }
            .take(maxDirs)
            .joinToString("\n") { (dir, files) ->
                val relative = runCatching { dir.relativeTo(home).path }.getOrDefault(dir.name)
                "· " + relative.ifBlank { "." } + "/ → " + files.size + " .so"
            }
    }

    private fun walk(root: File): List<File> {
        if (!root.exists()) return emptyList()
        val files = ArrayList<File>(64)
        val stack = ArrayDeque<File>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            val children = runCatching { current.listFiles() }.getOrNull() ?: continue
            for (child in children) {
                if (child.isDirectory) stack.addLast(child) else files.add(child)
            }
        }
        return files
    }
}
