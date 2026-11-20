package com.zyrovent.glauncher.data

import android.content.Context
import android.net.Uri
import com.zyrovent.glauncher.launch.RuntimeLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.PushbackInputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import org.tukaani.xz.XZInputStream

data class RuntimeStatus(
    val installed: Boolean,
    val javaPath: String,
    val sizeMb: Long
)

/** Resultado de la comprobación de un motor sin llegar a ejecutarlo. */
data class RuntimeCheck(val ok: Boolean, val report: String)

/**
 * Gestiona el motor Java que ejecuta el cliente: importación desde archivo, descarga
 * por URL, extracción (ZIP / tar.gz) y comprobación del binario.
 *
 * Ojo: el runtime debe ser una build para Android (Bionic, ARM64). Un JRE de Linux
 * (glibc) no arranca aquí sin proot/contenedor, aunque el archivo se instale.
 */
class RuntimeManager(
    context: Context,
    private val paths: GamePaths,
    private val downloader: Downloader = Downloader()
) {

    private val appContext = context.applicationContext

    fun status(major: Int): RuntimeStatus {
        val java = paths.runtimeJava(major)
        val dir = paths.runtimeDir(major)
        val size = if (dir.exists()) {
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } else {
            0L
        }
        return RuntimeStatus(
            installed = java.exists(),
            javaPath = java.absolutePath,
            sizeMb = size / (1024L * 1024L)
        )
    }

    fun installed(): List<Int> = paths.installedRuntimes()

    /** Arquitectura del binario según la cabecera ELF. */
    fun abiInfo(major: Int): String {
        val java = paths.runtimeJava(major)
        if (!java.exists()) return "sin runtime"
        return runCatching {
            val header = ByteArray(20)
            val read = java.inputStream().use { it.read(header) }
            if (read < 20 || header[0] != 0x7F.toByte() || header[1] != 'E'.code.toByte()) {
                return@runCatching "no es un binario ELF (¿script?)"
            }
            val machine = (header[18].toInt() and 0xFF) or ((header[19].toInt() and 0xFF) shl 8)
            when (machine) {
                183 -> "AArch64"
                40 -> "ARM"
                62 -> "x86-64"
                3 -> "x86"
                else -> "ELF machine $machine"
            }
        }.getOrElse { "no se pudo leer: ${it.message}" }
    }

    /**
     * Comprueba que el motor está completo **sin ejecutar nada**.
     *
     * Android 10+ (targetSdk 29) prohíbe hacer exec() de binarios del directorio
     * de la app, así que un `bin/java -version` siempre fallaría con EACCES. El
     * juego se lanza en proceso vía JLI_Launch, así que basta con verificar que
     * están las piezas que ese arranque necesita y que el binario es ELF de la
     * arquitectura correcta.
     */
    fun check(major: Int): RuntimeCheck {
        val home = paths.runtimeDir(major)
        if (!home.exists()) return RuntimeCheck(false, "No está instalado en ${home.absolutePath}")

        val java = paths.runtimeJava(major)
        if (!java.exists()) return RuntimeCheck(false, "Falta bin/java en ${home.absolutePath}")

        val abi = abiInfo(major)
        if (abi.startsWith("no es") || abi.startsWith("sin ") || abi.startsWith("ELF machine") ||
            abi.startsWith("no se pudo")
        ) {
            return RuntimeCheck(false, "bin/java no es un ejecutable válido: $abi")
        }

        // El arranque en proceso usa JNI_CreateJavaVM desde libjvm.so, así que
        // libjli es opcional: los builds de Termux no siempre lo traen y antes
        // esto bloqueaba un motor que sí era capaz de arrancar.
        val jli = RuntimeLayout.jli(home)

        val jvm = RuntimeLayout.jvm(home)
            ?: return RuntimeCheck(
                false,
                "Falta libjvm.so: el motor está incompleto.\n" + RuntimeLayout.tree(home)
            )

        return RuntimeCheck(
            true,
            "ELF $abi · ${jvm.name} ✓ · " +
                (if (jli != null) "${jli.name} ✓ · " else "sin libjli (no hace falta) · ") +
                RuntimeLayout.sharedLibs(home).size + " .so en " +
                RuntimeLayout.libDirs(home).size + " carpetas · listo para arrancar en " +
                "proceso (sin exec, válido en Android 10+)"
        )
    }

    suspend fun importZip(uri: Uri, major: Int, onLog: (String) -> Unit): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                withStaging { staging ->
                    onLog("Extrayendo el archivo seleccionado...")
                    val stream = appContext.contentResolver.openInputStream(uri)
                        ?: error("No se pudo abrir el archivo seleccionado")
                    stream.use { input -> extractAny(input, staging) }
                    installFrom(staging, major, onLog)
                }
            }
        }

    suspend fun downloadArchive(
        url: String,
        major: Int,
        onLog: (String) -> Unit,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            withStaging { staging ->
                val trimmed = url.trim()
                val fileName = trimmed.substringBefore('?').substringAfterLast('/').ifBlank { "runtime.zip" }
                val archive = File(appContext.cacheDir, "jre-download/$fileName")
                onLog("Descargando $trimmed")
                downloader.download(trimmed, archive) { done, total ->
                    if (total > 0) onProgress((done.toDouble() / total).toFloat().coerceIn(0f, 1f))
                }
                onLog("Descarga completa (${archive.length() / (1024 * 1024)} MB). Extrayendo...")
                archive.inputStream().use { input -> extractAny(input, staging) }
                archive.delete()
                installFrom(staging, major, onLog)
            }
        }
    }

    /**
     * Copia un .deb (formato de paquete de Termux) a [staging]. Los OpenJDK de
     * Termux se distribuyen así: dentro del `ar` va `data.tar.xz` con el prefix.
     */
    private fun installFromDeb(deb: File, staging: File, onLog: (String) -> Unit) {
        val header = ByteArray(8)
        java.io.RandomAccessFile(deb, "r").use { raf ->
            raf.readFully(header)
            if (String(header) != "!<arch>\n") {
                error("El archivo no es un paquete .deb válido")
            }
            var found = false
            while (raf.filePointer + 60 <= raf.length()) {
                val entry = ByteArray(60)
                val read = raf.read(entry)
                if (read < 60) break
                val name = String(entry, 0, 16).trim()
                val size = if (read >= 48) String(entry, 48, minOf(10, read - 48)).trim().toIntOrNull() else null
                if (name.startsWith("data.tar")) {
                    if (size == null) break
                    onLog("Paquete deb: extrayendo $name (${size / (1024 * 1024)} MB)")
                    val offset = raf.filePointer
                    extractAny(FixedLengthInputStream(raf, offset, size), staging)
                    found = true
                    break
                }
                if (size != null && size > 0) {
                    val entryEnd = raf.filePointer + size + (size and 1)
                    raf.seek(entryEnd)
                }
            }
            if (!found) error("El .deb no contiene data.tar")
        }
    }

    /** Lectura acotada a un tramo del archivo (para no desbordar la entrada). */
    private class FixedLengthInputStream(
        private val raf: java.io.RandomAccessFile,
        private val start: Long,
        private val size: Int
    ) : InputStream() {
        private var pos = start
        private var remaining = size.toLong()

        override fun read(): Int {
            if (remaining <= 0) return -1
            raf.seek(pos)
            val b = raf.read()
            if (b < 0) return -1
            pos++; remaining--
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0) return -1
            raf.seek(pos)
            val toRead = minOf(len.toLong(), remaining).toInt()
            val read = raf.read(b, off, toRead)
            if (read <= 0) return -1
            pos += read; remaining -= read
            return read
        }
    }

    /** Copia el contenido extraído al directorio definitivo y valida el binario. */
    private fun installFrom(staging: File, major: Int, onLog: (String) -> Unit) {
        val javaRoot = locateJavaRoot(staging)
            ?: error("El archivo no contiene bin/java. ¿Es un runtime para Android?")
        onLog("Runtime encontrado: ${javaRoot.name}")

        val target = paths.runtimeDir(major)
        target.deleteRecursively()
        target.mkdirs()
        javaRoot.copyRecursively(target, overwrite = true)

        val java = paths.runtimeJava(major)
        if (!java.exists()) error("La copia del runtime quedó incompleta")
        java.setExecutable(true, false)
        File(target, "bin").listFiles()?.forEach { it.setExecutable(true, false) }

        val abi = abiInfo(major)
        onLog("Motor Java $major instalado · ABI: $abi · ${java.absolutePath}")
        if (abi != "AArch64" && !abi.startsWith("no es") && !abi.startsWith("ELF")) {
            onLog("Aviso: se esperaba un binario AArch64. Puede no arrancar en este dispositivo.")
        }
    }

    fun removeRuntime(major: Int): Boolean {
        val target = paths.runtimeDir(major)
        target.walkTopDown().forEach { entry ->
            if (entry.isDirectory) entry.setWritable(true, true)
        }
        return target.deleteRecursively()
    }

    companion object {
        private const val TERMUX_MAIN = "https://packages.termux.dev/apt/termux-main/"

        /** JRE 17 para Android ARM64 (OpenJDK 17.0.5, build de PojavLauncher). */
        const val JRE17_URL =
            "https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch/releases/" +
                "download/jre17-ec28559/jre17-arm64-20210825-release.tar.xz"

        /** OpenJDK 17.0.21.1 para Android (paquete .deb biónico de Termux, ARM64). */
        const val JRE17_TERMUX_URL = TERMUX_MAIN + "pool/main/o/openjdk-17/openjdk-17_17.0.21.1_aarch64.deb"

        /** OpenJDK 21.0.13.1 para Android (paquete .deb biónico de Termux, ARM64). */
        const val JRE21_URL = TERMUX_MAIN + "pool/main/o/openjdk-21/openjdk-21_21.0.13.1_aarch64.deb"

        /** OpenJDK 25.0.5.1 para Android (paquete .deb biónico de Termux, ARM64). */
        const val JRE25_URL = TERMUX_MAIN + "pool/main/o/openjdk-25/openjdk-25_25.0.5.1_aarch64.deb"

        /**
         * OpenJDK 8 para Android (bionic, ARM64). Lo necesita Minecraft 1.16.5 y
         * anteriores. El tarball de Pojav es de iOS y falla con `syntax error`,
         * así que se usa el build de AngelAuraMC, que sí es Bionic.
         */
        const val JRE8_URL =
            "https://github.com/AngelAuraMC/angelauramc-openjdk-build/releases/download/" +
                "download_jre8/jre8-android-arm64.tar.xz"

        fun presetUrl(major: Int): String? = when (major) {
            8 -> JRE8_URL
            17 -> JRE17_URL
            21 -> JRE21_URL
            25 -> JRE25_URL
            else -> null
        }

        /** Alternativa más reciente para 17 (Termux 17.0.21 en .deb). */
        fun termuxPresetUrl(major: Int): String? = when (major) {
            17 -> JRE17_TERMUX_URL
            21 -> JRE21_URL
            25 -> JRE25_URL
            else -> null
        }
    }

    private inline fun <T> withStaging(block: (File) -> T): T {
        val staging = File(appContext.cacheDir, "jre-import").apply {
            deleteRecursively()
            mkdirs()
        }
        return try {
            block(staging)
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun locateJavaRoot(staging: File): File? {
        if (File(staging, "bin/java").exists()) return staging
        return staging.walkTopDown()
            .firstOrNull { it.isDirectory && File(it, "bin/java").exists() }
    }

    private fun unzip(input: InputStream, destination: File) {
        val basePath = destination.canonicalFile.path
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val target = File(destination, entry.name).canonicalFile
                if (!target.path.startsWith(basePath)) error("Entrada no válida: ${entry.name}")
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { output -> zip.copyTo(output) }
                    if (entry.name.endsWith("bin/java")) target.setExecutable(true)
                }
                zip.closeEntry()
            }
        }
    }

    /** Lector mínimo de tar (sin enlaces simbólicos) para los .tar.gz de OpenJDK. */
    private fun untarGz(input: InputStream, destination: File) {
        GZIPInputStream(input.buffered()).use { tar -> untar(tar, destination) }
    }

    /** Decide el formato por la firma del archivo: xz, gzip o zip. */
    private fun extractAny(input: InputStream, destination: File) {
        val pushback = PushbackInputStream(input.buffered(), 8)
        val header = ByteArray(8)
        var read = 0
        while (read < header.size) {
            val chunk = pushback.read(header, read, header.size - read)
            if (chunk <= 0) break
            read += chunk
        }
        pushback.unread(header, 0, read)
        val head = header.take(read).toByteArray()
        val isXz = read >= 6 && head[0] == 0xFD.toByte() &&
            head.sliceArray(1..4).contentEquals("7zXZ".toByteArray())
        val isGzip = read >= 2 && (head[0].toInt() and 0xFF) == 0x1F &&
            (head[1].toInt() and 0xFF) == 0x8B
        val isZip = read >= 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()
        // Cabecera "ar" ("!<arch>\n"): es un paquete .deb de Debian/Termux.
        val isAr = read >= 8 &&
            head.sliceArray(0..7).contentEquals("!<arch>\n".toByteArray(Charsets.US_ASCII))

        when {
            isAr -> unarDeb(pushback, destination)
            isZip -> unzip(pushback, destination)
            isGzip -> GZIPInputStream(pushback).use { untar(it, destination) }
            isXz -> XZInputStream(pushback).use { untar(it, destination) }
            else -> error("Formato no reconocido: usa .zip, .deb, .tar.gz o .tar.xz")
        }
    }

    /**
     * Extrae el miembro `data.tar.*` de un .deb sin materializar el paquete entero.
     * Los .deb de Termux son `ar` con dos miembros: control.tar.* y data.tar.*.
     */
    private fun unarDeb(input: InputStream, destination: File) {
        val magic = ByteArray(8)
        if (readExact(input, magic) != 8 || !magic.contentEquals("!<arch>\n".toByteArray())) {
            error("El paquete .deb no tiene cabecera ar válida")
        }
        while (true) {
            val header = ByteArray(60)
            if (readExact(input, header) != 60) break
            val name = String(header, 0, 16).trim().removeSuffix("/")
            val size = String(header, 48, 10).trim().toLongOrNull() ?: break
            val padded = size + (size % 2)

            if (name.startsWith("data.tar")) {
                extractAny(BoundedInputStream(input, size), destination)
                return
            }
            if (skipFully(input, padded) != padded) break
        }
        error("El .deb no contiene data.tar (¿es un paquete de runtime?)")
    }

    private fun readExact(input: InputStream, buffer: ByteArray): Int {
        var read = 0
        while (read < buffer.size) {
            val chunk = input.read(buffer, read, buffer.size - read)
            if (chunk <= 0) break
            read += chunk
        }
        return read
    }

    private fun skipFully(input: InputStream, count: Long): Long {
        var skipped = 0L
        val buffer = ByteArray(8 * 1024)
        while (skipped < count) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), count - skipped).toInt())
            if (read <= 0) break
            skipped += read
        }
        return skipped
    }

    /** Vista acotada de un miembro del .deb. */
    private class BoundedInputStream(
        private val source: InputStream,
        private var remaining: Long
    ) : InputStream() {
        override fun read(): Int {
            if (remaining <= 0) return -1
            val value = source.read()
            if (value >= 0) remaining--
            return value
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0) return -1
            val count = source.read(b, off, minOf(len.toLong(), remaining).toInt())
            if (count > 0) remaining -= count
            return count
        }

        override fun close() = Unit
    }

    /**
     * Lee tar puro. Soporta: ficheros, directorios, nombres largos (GNU "L"),
     * PAX ("x"/"g") y enlaces simbólicos (se resuelven copiando el destino cuando
     * ya está extraído). Formatos gzip y xz los envuelve el llamante.
     */
    private fun untar(input: InputStream, destination: File) {
        val basePath = destination.canonicalFile.path
        val header = ByteArray(512)
        var pendingLongName: String? = null
        val pendingLinks = mutableListOf<Pair<File, String>>()

        fun safeTarget(relative: String): File {
            val target = File(destination, relative).canonicalFile
            if (!target.path.startsWith(basePath)) error("Entrada no válida: $relative")
            return target
        }

        while (true) {
            if (!readFully(input, header)) break
            if (header.all { it == 0.toByte() }) break

            var name = String(header, 0, 100).trimEnd('\u0000')
            val prefix = String(header, 345, 155).trimEnd('\u0000')
            if (prefix.isNotEmpty()) name = "$prefix/$name"

            val size = parseSize(String(header, 124, 12))
            val mode = parseOctal(String(header, 100, 8))?.toInt() ?: 0
            val type = header[156].toInt().toChar()
            val padding = (512 - (size % 512)) % 512

            when {
                type == 'L' -> {
                    // Siguiente entrada con nombre largo (GNU tar).
                    pendingLongName = String(readBytesOf(input, size), Charsets.UTF_8).trimEnd('\n', '\u0000')
                    skipExactly(input, padding)
                    continue
                }
                type == 'x' || type == 'g' || type == 'K' || type == 'D' -> {
                    // Metadatos PAX: solo consumir.
                    skipExactly(input, size + padding)
                    continue
                }
            }

            val longName = pendingLongName
            if (longName != null) {
                name = longName
                pendingLongName = null
            }

            if (name.isBlank()) {
                skipExactly(input, size + padding)
                continue
            }

            val target = safeTarget(name)
            when (type) {
                '5' -> target.mkdirs()
                '1' -> {
                    // Enlace duro: si el original ya existe, se copia.
                    val linkName = String(header, 157, 100).trimEnd('\u0000')
                    pendingLinks += target to linkName
                    skipExactly(input, size + padding)
                }
                '2' -> {
                    val linkName = String(header, 157, 100).trimEnd('\u0000')
                    pendingLinks += target to linkName
                    skipExactly(input, size + padding)
                }
                else -> {
                    if (name.endsWith("/")) {
                        target.mkdirs()
                        skipExactly(input, size + padding)
                    } else {
                        target.parentFile?.mkdirs()
                        copyExactly(input, target.outputStream(), size)
                        if (mode != 0) target.setExecutable((mode and 0x49) != 0, false)
                        skipExactly(input, padding)
                    }
                }
            }
        }

        // Resolver enlaces: se copia el contenido si está disponible.
        pendingLinks.forEach { (link, linkName) ->
            val resolved = if (linkName.startsWith('/')) File(linkName) else File(link.parentFile, linkName)
            runCatching {
                val source = resolved.canonicalFile
                if (source.isFile) {
                    link.parentFile?.mkdirs()
                    link.delete()
                    source.copyTo(link, overwrite = true)
                    link.setExecutable(source.canExecute(), false)
                }
            }
        }
    }

    private fun readBytesOf(input: InputStream, size: Long): ByteArray {
        val buffer = ByteArray(size.toInt().coerceAtLeast(0))
        var read = 0
        while (read < buffer.size) {
            val chunk = input.read(buffer, read, buffer.size - read)
            if (chunk <= 0) break
            read += chunk
        }
        return buffer.copyOf(read)
    }

    private fun parseSize(raw: String): Long {
        val clean = raw.trim('\u0000', ' ')
        return clean.toLongOrNull(8) ?: run {
            // Codificación base-256 (GNU) para tamaños muy grandes.
            if (clean.isNotEmpty() && clean[0].code >= 0x80) {
                var value = 0L
                clean.forEach { value = (value shl 8) or (it.code.toLong() and 0xFF) }
                value and 0x7FFFFFFFFFFFFFFFL
            } else 0L
        }
    }

    private fun parseOctal(raw: String): Long? =
        raw.trim('\u0000', ' ').takeIf { it.isNotEmpty() }?.toLongOrNull(8)

    private fun readFully(input: InputStream, buffer: ByteArray): Boolean {
        var read = 0
        while (read < buffer.size) {
            val chunk = input.read(buffer, read, buffer.size - read)
            if (chunk <= 0) return false
            read += chunk
        }
        return true
    }

    private fun copyExactly(input: InputStream, output: java.io.OutputStream, size: Long) {
        output.use { out ->
            var remaining = size
            val buffer = ByteArray(64 * 1024)
            while (remaining > 0) {
                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read <= 0) break
                out.write(buffer, 0, read)
                remaining -= read
            }
        }
    }

    private fun skipExactly(input: InputStream, size: Long) {
        var remaining = size
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) break
            remaining -= skipped
        }
    }
}
