package com.zyrovent.glauncher.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder

/** Un proyecto del catálogo de Modrinth. */
data class ModHit(
    val id: String,
    val slug: String,
    val title: String,
    val description: String,
    val iconUrl: String?,
    val downloads: Long,
    val author: String,
    val type: String
) {
    val downloadsLabel: String
        get() = when {
            downloads >= 1_000_000 -> "%.1fM".format(downloads / 1_000_000.0)
            downloads >= 1_000 -> "${downloads / 1_000}K"
            else -> downloads.toString()
        }
}

/** Archivo descargable de un proyecto. */
data class ModFile(val url: String, val filename: String, val versionName: String)

data class ModCategory(val apiType: String, val label: String, val folder: String)

/**
 * Catálogo de Modrinth (API pública v2, sin clave). Busca mods, packs de
 * texturas, shaders y modpacks, y descarga el archivo compatible con la versión
 * de Minecraft que esté activa en el launcher.
 */
class ModsRepository(private val downloader: Downloader = Downloader()) {

    private val api = "https://api.modrinth.com/v2"

    val categories = listOf(
        ModCategory("mod", "Mods", "mods"),
        ModCategory("resourcepack", "Texturas", "resourcepacks"),
        ModCategory("shader", "Shaders", "shaderpacks"),
        ModCategory("modpack", "Modpacks", "modpacks")
    )

    fun search(query: String, category: ModCategory, limit: Int = 20): List<ModHit> {
        val facets = """[["project_type:${category.apiType}"]]"""
        val url = buildString {
            append(api).append("/search?limit=").append(limit)
            append("&index=relevance")
            if (query.isNotBlank()) append("&query=").append(encode(query.trim()))
            append("&facets=").append(encode(facets))
        }
        val root = JSONObject(http(url))
        val hits = root.optJSONArray("hits") ?: JSONArray()
        val result = ArrayList<ModHit>(hits.length())
        for (i in 0 until hits.length()) {
            val item = hits.optJSONObject(i) ?: continue
            val id = item.optString("project_id").ifBlank { item.optString("slug") }
            if (id.isBlank()) continue
            result.add(
                ModHit(
                    id = id,
                    slug = item.optString("slug"),
                    title = item.optString("title").ifBlank { "Sin título" },
                    description = item.optString("description"),
                    iconUrl = item.optString("icon_url").ifBlank { null },
                    downloads = item.optLong("downloads", 0L),
                    author = item.optString("author").ifBlank { "—" },
                    type = item.optString("project_type").ifBlank { category.apiType }
                )
            )
        }
        return result
    }

    /** Primer archivo del proyecto compatible con [gameVersion], prefiriendo Fabric. */
    fun latestFile(projectId: String, gameVersion: String, preferLoader: String = "fabric"): ModFile? {
        val versions = JSONArray(http("$api/project/$projectId/version"))
        var fallback: ModFile? = null
        for (i in 0 until versions.length()) {
            val version = versions.optJSONObject(i) ?: continue
            if (!listContains(version.optJSONArray("game_versions"), gameVersion)) continue
            val files = version.optJSONArray("files") ?: continue
            val file = (0 until files.length())
                .mapNotNull { files.optJSONObject(it) }
                .firstOrNull { it.optBoolean("primary", false) }
                ?: files.optJSONObject(0)
                ?: continue
            val url = file.optString("url")
            val filename = file.optString("filename")
            if (url.isBlank() || filename.isBlank()) continue
            val candidate = ModFile(url, filename, version.optString("version_number"))
            val loaders = version.optJSONArray("loaders")
            if (loaders == null || listContains(loaders, preferLoader) || listContains(loaders, "minecraft")) {
                return candidate
            }
            if (fallback == null) fallback = candidate
        }
        return fallback
    }

    fun download(file: ModFile, targetDir: File, onProgress: (Float) -> Unit = {}): File {
        targetDir.mkdirs()
        val dest = File(targetDir, file.filename)
        return downloader.download(file.url, dest) { done, total ->
            if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
        }
    }

    private fun listContains(array: JSONArray?, value: String): Boolean {
        if (array == null) return false
        for (i in 0 until array.length()) {
            if (array.optString(i).equals(value, ignoreCase = true)) return true
        }
        return false
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    /** Todas las llamadas van por TLS 1.2 explícito (ver [Tls]). */
    private fun http(url: String): String = Tls.read(url)
}
