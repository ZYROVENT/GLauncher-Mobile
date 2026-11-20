package com.zyrovent.glauncher.data

import java.io.InputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

/** Descargas HTTP simples con verificación SHA-1 y callback de progreso. */
class Downloader {

    private fun wrapDecompression(connection: HttpURLConnection, rawInput: InputStream): InputStream {
        val encoding = connection.contentEncoding?.lowercase() ?: ""
        return when {
            encoding.contains("gzip") -> GZIPInputStream(rawInput)
            encoding.contains("deflate") -> InflaterInputStream(rawInput)
            else -> rawInput
        }
    }

    fun fetchText(url: String): String {
        val connection = open(url)
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code al leer $url")
            val input = wrapDecompression(connection, connection.inputStream)
            return input.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Descarga [url] en [dest]. Si el archivo ya existe y su SHA-1 coincide con
     * [expectedSha1] no se vuelve a descargar.
     */
    fun download(
        url: String,
        dest: File,
        expectedSha1: String? = null,
        expectedSize: Long = -1L,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> }
    ): File {
        if (dest.exists() && expectedSha1 != null && sha1(dest).equals(expectedSha1, ignoreCase = true)) {
            onProgress(dest.length(), dest.length())
            return dest
        }
        dest.parentFile?.mkdirs()
        val temp = File(dest.parentFile, dest.name + ".part")
        if (temp.exists()) temp.delete()

        val connection = open(url)
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code al descargar $url")
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: expectedSize
            var downloaded = 0L
            val digest = MessageDigest.getInstance("SHA-1")

            val rawStream = connection.inputStream
            // Si el servidor envía gzip en un binario, descomprimirlo si no es un JAR o ya viene comprimido
            val input = wrapDecompression(connection, rawStream)

            input.use { inStream ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                    while (true) {
                        val read = inStream.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                    }
                    output.flush()
                }
            }

            if (expectedSha1 != null) {
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(expectedSha1, ignoreCase = true)) {
                    temp.delete()
                    throw IOException("SHA-1 inválido para ${dest.name}: se esperaba $expectedSha1 y llegó $actual")
                }
            }
            if (dest.exists()) dest.delete()
            if (!temp.renameTo(dest)) throw IOException("No se pudo mover ${temp.name} a ${dest.name}")
            return dest
        } finally {
            connection.disconnect()
        }
    }

    fun sha1(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "GLauncher/1.0 (Android)")
            setRequestProperty("Accept-Encoding", "gzip, deflate")
            setRequestProperty("Connection", "keep-alive")
        }
}
