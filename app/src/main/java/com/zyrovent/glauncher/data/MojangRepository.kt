package com.zyrovent.glauncher.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Cliente del API público de Mojang. El manifest y los metadatos son los mismos
 * que usa el launcher oficial; no requiere autenticación.
 */
class MojangRepository(
    private val store: LauncherStore,
    private val downloader: Downloader = Downloader()
) {

    suspend fun versions(refresh: Boolean): VersionManifest = withContext(Dispatchers.IO) {
        if (!refresh) {
            store.cachedManifestJson()?.let { return@withContext parseManifest(it) }
        }
        try {
            val json = downloader.fetchText(MANIFEST_URL)
            store.saveManifestJson(json)
            parseManifest(json)
        } catch (error: Exception) {
            // Sin red: si hay una copia local seguimos funcionando.
            store.cachedManifestJson()?.let { return@withContext parseManifest(it) }
            throw error
        }
    }

    suspend fun version(id: String): VersionSummary {
        versions(refresh = false).versions.firstOrNull { it.id == id }?.let { return it }
        // Puede ser una versión publicada después de la última caché: reintenta en red.
        return versions(refresh = true).versions.firstOrNull { it.id == id }
            ?: throw IllegalStateException("La versión $id no aparece en el manifest de Mojang")
    }

    suspend fun files(version: VersionSummary, cachedJson: String? = null): VersionFiles = withContext(Dispatchers.IO) {
        val raw = if (!cachedJson.isNullOrBlank()) {
            cachedJson
        } else {
            downloader.fetchText(version.metadataUrl)
        }
        val json = JSONObject(raw)
        val client = json.getJSONObject("downloads").getJSONObject("client")
        val assetIndex = json.optJSONObject("assetIndex")
        VersionFiles(
            id = json.optString("id", version.id),
            clientUrl = client.getString("url"),
            clientSha1 = client.optString("sha1"),
            clientSize = client.optLong("size"),
            assetIndexId = assetIndex?.optString("id"),
            assetIndexUrl = assetIndex?.optString("url"),
            assetIndexSha1 = assetIndex?.optString("sha1"),
            metadataUrl = version.metadataUrl,
            metadataJson = raw,
            libraries = parseLibraries(json),
            javaMajor = json.optJSONObject("javaVersion")?.optInt("majorVersion")?.takeIf { it > 0 }
        )
    }

    /** Librerías del classpath, solo las válidas en Linux/Android. */
    private fun parseLibraries(json: JSONObject): List<LibraryArtifact> {
        val array = json.optJSONArray("libraries") ?: return emptyList()
        val libraries = mutableListOf<LibraryArtifact>()
        for (i in 0 until array.length()) {
            val library = array.optJSONObject(i) ?: continue
            val artifact = library.optJSONObject("downloads")?.optJSONObject("artifact")
                ?: continue
            if (!allowsLinux(library)) continue

            val path = artifact.optString("path")
            val artifactUrl = artifact.optString("url")
            val repository = library.optString("url").ifBlank { DEFAULT_LIBRARY_REPO }
            val url = when {
                artifactUrl.isNotBlank() -> artifactUrl
                path.isNotBlank() -> repository.trimEnd('/') + "/" + path
                else -> continue
            }
            if (path.isBlank() || url.isBlank()) continue
            libraries += LibraryArtifact(
                path = path,
                url = url,
                sha1 = artifact.optString("sha1"),
                size = artifact.optLong("size")
            )
        }
        return libraries
    }

    /** Aplica las reglas de la metadata: solo se admite lo que vale en "linux". */
    private fun allowsLinux(library: JSONObject): Boolean {
        val rules = library.optJSONArray("rules") ?: return true
        var allowed = false
        for (i in 0 until rules.length()) {
            val rule = rules.optJSONObject(i) ?: continue
            val osName = rule.optJSONObject("os")?.optString("name")
            val matches = osName == null || osName == "linux"
            if (matches) allowed = rule.optString("action", "allow") == "allow"
        }
        return allowed
    }

    private fun parseManifest(json: String): VersionManifest {
        val root = JSONObject(json)
        val latest = root.optJSONObject("latest")
        val array = root.getJSONArray("versions")
        val versions = buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val id = item.optString("id")
                val url = item.optString("url")
                if (id.isNotBlank() && url.isNotBlank()) {
                    add(
                        VersionSummary(
                            id = id,
                            type = item.optString("type", "release"),
                            releaseTime = item.optString("releaseTime", ""),
                            metadataUrl = url
                        )
                    )
                }
            }
        }
        return VersionManifest(
            latestRelease = latest?.optString("release").orEmpty().ifBlank { versions.firstOrNull { !it.isSnapshot }?.id.orEmpty() },
            latestSnapshot = latest?.optString("snapshot").orEmpty(),
            versions = versions
        )
    }

    private companion object {
        const val MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
        const val DEFAULT_LIBRARY_REPO = "https://libraries.minecraft.net/"
    }
}
