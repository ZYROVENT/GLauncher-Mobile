package com.zyrovent.glauncher.data

import com.zyrovent.glauncher.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** Aviso publicado en Supabase (`public.notifications`). */
data class RemoteNotification(
    val id: String,
    val title: String,
    val body: String,
    val kind: String = "info",
    val actionUrl: String? = null,
    val actionLabel: String? = null,
    val publishedAt: String = "",
    val read: Boolean = false
)

/** Versión publicada del launcher (`public.launcher_releases`). */
data class RemoteRelease(
    val version: String,
    val channel: String = "stable",
    val title: String = "",
    val notes: String = "",
    val downloadUrl: String? = null,
    val mandatory: Boolean = false
)

/**
 * Cliente mínimo de Supabase (PostgREST) con la clave anon.
 *
 * Sin sesión de Supabase Auth se leen las tablas públicas: el catálogo de
 * versiones (`launcher_releases`, lectura para todo el mundo) y los avisos
 * generales (`notifications`). Cuando la app tenga sesión, se manda el JWT y se
 * usan las RPC de `supabase/glauncher.sql` (list_my_notifications, etc.).
 *
 * Las claves viven en `local.properties` (SUPABASE_URL y SUPABASE_ANON) y llegan
 * aquí por BuildConfig: no hay nada escrito en el código.
 */
object SupabaseApi {

    val configured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON.isNotBlank()

    private fun base(): String = BuildConfig.SUPABASE_URL.trimEnd('/')

    private fun headers(extra: Map<String, String> = emptyMap()): Map<String, String> {
        val map = mutableMapOf(
            "apikey" to BuildConfig.SUPABASE_ANON,
            "Authorization" to "Bearer ${BuildConfig.SUPABASE_ANON}",
            "Accept" to "application/json"
        )
        map.putAll(extra)
        return map
    }

    /** GET a /rest/v1/<path>. */
    fun get(path: String, jwt: String? = null): String {
        val url = "${base()}/rest/v1/$path"
        val connection = Tls.open(url, timeoutMs = 15_000)
        val token = jwt ?: BuildConfig.SUPABASE_ANON
        try {
            headers().forEach { (key, value) ->
                if (key != "Authorization") connection.setRequestProperty(key, value)
            }
            connection.setRequestProperty("Authorization", "Bearer $token")
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("HTTP $code")
            return text
        } finally {
            connection.disconnect()
        }
    }

    /** POST a /rest/v1/rpc/<función>. */
    fun rpc(function: String, body: String = "{}", jwt: String? = null): String {
        val url = "${base()}/rest/v1/rpc/$function"
        val connection = Tls.open(url, timeoutMs = 15_000)
        val token = jwt ?: BuildConfig.SUPABASE_ANON
        try {
            val payload = body.toByteArray(Charsets.UTF_8)
            connection.requestMethod = "POST"
            connection.doOutput = true
            headers().forEach { (key, value) -> connection.setRequestProperty(key, value) }
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setFixedLengthStreamingMode(payload.size)
            connection.outputStream.use { it.write(payload) }
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("HTTP $code")
            return text
        } finally {
            connection.disconnect()
        }
    }

    /** Avisos activos, del más nuevo al más viejo (lectura pública). */
    suspend fun notifications(limit: Int = 30): List<RemoteNotification> = withContext(Dispatchers.IO) {
        if (!configured) return@withContext emptyList()
        val raw = get(
            "notifications?select=id,title,body,kind,action_url,action_label,published_at" +
                "&published_at=lte.now()&order=published_at.desc&limit=$limit"
        )
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id")
                val title = item.optString("title")
                if (id.isBlank() || title.isBlank()) continue
                add(
                    RemoteNotification(
                        id = id,
                        title = title,
                        body = item.optString("body"),
                        kind = item.optString("kind", "info").ifBlank { "info" },
                        actionUrl = item.optString("action_url").takeIf { it.isNotBlank() },
                        actionLabel = item.optString("action_label").takeIf { it.isNotBlank() },
                        publishedAt = item.optString("published_at")
                    )
                )
            }
        }
    }

    /** Última versión publicada del canal (null si no hay ninguna). */
    suspend fun latestRelease(channel: String = "stable"): RemoteRelease? = withContext(Dispatchers.IO) {
        if (!configured) return@withContext null
        val raw = get(
            "launcher_releases?select=version,channel,title,notes,download_url,mandatory" +
                "&channel=eq.$channel&published_at=lte.now()&order=published_at.desc&limit=1"
        )
        val array = JSONArray(raw)
        val item: JSONObject = array.optJSONObject(0) ?: return@withContext null
        val version = item.optString("version")
        if (version.isBlank()) return@withContext null
        RemoteRelease(
            version = version,
            channel = item.optString("channel", channel),
            title = item.optString("title"),
            notes = item.optString("notes"),
            downloadUrl = item.optString("download_url").takeIf { it.isNotBlank() },
            mandatory = item.optBoolean("mandatory", false)
        )
    }

    /** Marca un aviso como leído en el servidor (requiere sesión de Supabase). */
    suspend fun markRead(id: String, jwt: String? = null) = withContext(Dispatchers.IO) {
        if (!configured || jwt.isNullOrBlank()) return@withContext
        runCatching { rpc("mark_notification_read", "{\"p_id\":\"$id\"}", jwt) }
    }
}
