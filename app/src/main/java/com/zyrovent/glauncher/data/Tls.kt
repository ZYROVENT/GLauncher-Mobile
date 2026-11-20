package com.zyrovent.glauncher.data

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext

/**
 * Conexiones HTTPS del launcher. Android 10+ ya negocia TLS 1.2/1.3, pero algunas
 * ROMs dejan TLS 1.0 habilitado: aquí el contexto se restringe a TLS 1.2 para las
 * llamadas a las APIs (búsqueda, catálogo, descargas).
 */
object Tls {

    const val USER_AGENT = "GLauncher/1.1 (Android)"

    private val socketFactory by lazy {
        runCatching {
            SSLContext.getInstance("TLSv1.2").apply { init(null, null, null) }.socketFactory
        }.getOrNull()
    }

    fun open(url: String, userAgent: String = USER_AGENT, timeoutMs: Int = 20_000): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = timeoutMs
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", userAgent)
            (this as? HttpsURLConnection)?.let { secure ->
                socketFactory?.let { secure.sslSocketFactory = it }
            }
        }

    /** POST con cuerpo JSON (API interna de YouTube o la de GLauncher). */
    fun postJson(
        url: String,
        body: String,
        timeoutMs: Int = 20_000,
        token: String? = null
    ): String {
        val connection = open(url, timeoutMs = timeoutMs)
        try {
            val payload = body.toByteArray(Charsets.UTF_8)
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            connection.setFixedLengthStreamingMode(payload.size)
            connection.outputStream.use { it.write(payload) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val errorBody = runCatching {
                    connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                }.getOrDefault("")
                val message = runCatching {
                    org.json.JSONObject(errorBody).let {
                        it.optString("message").ifBlank { null }
                            ?: it.optString("error").ifBlank { null }
                            ?: it.optString("detail").ifBlank { null }
                    }
                }.getOrNull()
                throw IOException(message ?: when (code) {
                    400 -> "Datos inválidos. Revisa los campos."
                    401 -> "Usuario, contraseña o código de seguridad incorrectos."
                    403 -> "Acceso denegado."
                    404 -> "Servicio no encontrado."
                    429 -> "Demasiados intentos. Espera un momento."
                    500, 502, 503 -> "Error en el servidor. Intenta más tarde."
                    else -> "Error HTTP $code"
                })
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /** GET de texto con la respuesta ya cerrada. */
    fun read(
        url: String,
        userAgent: String = USER_AGENT,
        timeoutMs: Int = 25_000,
        token: String? = null
    ): String {
        val connection = open(url, userAgent, timeoutMs)
        token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                val errorBody = runCatching {
                    connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                }.getOrDefault("")
                val message = runCatching {
                    org.json.JSONObject(errorBody).let {
                        it.optString("message").ifBlank { null }
                            ?: it.optString("error").ifBlank { null }
                    }
                }.getOrNull()
                throw IOException(message ?: when (code) {
                    401 -> "Sesión expirada. Inicia sesión nuevamente."
                    403 -> "Acceso denegado."
                    429 -> "Demasiados intentos. Espera un momento."
                    500, 502, 503 -> "Error en el servidor. Intenta más tarde."
                    else -> "Error HTTP $code"
                })
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
