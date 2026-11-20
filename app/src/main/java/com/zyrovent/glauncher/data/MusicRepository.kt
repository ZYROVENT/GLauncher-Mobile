package com.zyrovent.glauncher.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/** Una canción encontrada en YouTube. */
data class MusicTrack(
    val id: String,
    val title: String,
    val uploader: String,
    val seconds: Int,
    val thumbnail: String? = null
) {
    val durationLabel: String
        get() {
            if (seconds <= 0) return "--:--"
            val minutes = seconds / 60
            val rest = seconds % 60
            return "%d:%02d".format(minutes, rest)
        }
}

/**
 * Búsqueda de vídeos de YouTube.
 *
 * Va primero al buscador propio de YouTube (API interna `youtubei`, cliente WEB,
 * que no pide cuenta ni clave propia) porque las instancias públicas de Piped y
 * Invidious están caídas o bloqueadas. Si esa vía falla, prueba los respaldos.
 * El audio/vídeo no se resuelve aquí: lo reproduce [com.zyrovent.glauncher.ui.screens.YouTubeWebPlayer]
 * con el ID que devuelve esta búsqueda.
 */
class MusicRepository {

    private val searchUrl =
        "https://www.youtube.com/youtubei/v1/search" +
            "?key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8&prettyPrint=false"

    private val piped = listOf(
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.adminforge.de"
    )

    private val invidious = listOf(
        "https://yewtu.be",
        "https://inv.nadeko.net"
    )

    fun search(query: String): List<MusicTrack> {
        val clean = query.trim()
        if (clean.isEmpty()) return emptyList()

        var reason: String? = null
        val direct = runCatching { searchYouTube(clean) }
            .onFailure { reason = it.message }
            .getOrNull()
        if (!direct.isNullOrEmpty()) return direct

        for (base in piped) {
            val result = runCatching { searchPiped(base, clean) }.getOrNull()
            if (!result.isNullOrEmpty()) return result
        }
        for (base in invidious) {
            val result = runCatching { searchInvidious(base, clean) }.getOrNull()
            if (!result.isNullOrEmpty()) return result
        }
        throw IOException(reason ?: "YouTube no devolvió resultados. Revisa tu conexión.")
    }

    // ---------------- YouTube (youtubei) ----------------

    private fun searchYouTube(query: String): List<MusicTrack> {
        val body = """{"context":{"client":{"clientName":"WEB","clientVersion":"2.20240726.00.00","hl":"es","gl":"ES"}},"query":${quote(query)}}"""
        val root = JSONObject(Tls.postJson(searchUrl, body))

        val renderers = ArrayList<JSONObject>()
        collectVideoRenderers(root, renderers)

        val seen = HashSet<String>()
        val tracks = ArrayList<MusicTrack>(renderers.size)
        for (renderer in renderers) {
            val id = renderer.optString("videoId")
            if (id.length != 11 || !seen.add(id)) continue
            val title = renderer.optJSONObject("title")
                ?.let { node ->
                    node.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                        ?: node.optString("simpleText")
                }
                .orEmpty()
            val uploader = renderer.optJSONObject("ownerText")
                ?.let { it.optJSONArray("runs")?.optJSONObject(0)?.optString("text") }
                ?: renderer.optJSONObject("longBylineText")
                    ?.let { it.optJSONArray("runs")?.optJSONObject(0)?.optString("text") }
                ?: renderer.optJSONObject("shortBylineText")
                    ?.let { it.optJSONArray("runs")?.optJSONObject(0)?.optString("text") }
                .orEmpty()
            val length = renderer.optJSONObject("lengthText")?.optString("simpleText").orEmpty()

            tracks.add(
                MusicTrack(
                    id = id,
                    title = title.ifBlank { "Sin título" },
                    uploader = uploader.ifBlank { "YouTube" },
                    seconds = parseClock(length),
                    thumbnail = "https://i.ytimg.com/vi/$id/mqdefault.jpg"
                )
            )
        }
        return tracks
    }

    /** Recorre el árbol de respuesta y recoge todos los `videoRenderer`. */
    private fun collectVideoRenderers(node: Any?, out: MutableList<JSONObject>) {
        when (node) {
            is JSONObject -> {
                node.optJSONObject("videoRenderer")?.let { out.add(it) }
                val keys = node.keys()
                while (keys.hasNext()) {
                    collectVideoRenderers(node.opt(keys.next()), out)
                }
            }
            is JSONArray -> {
                for (index in 0 until node.length()) {
                    collectVideoRenderers(node.opt(index), out)
                }
            }
        }
    }

    /** "4:03" o "1:02:11" a segundos. */
    private fun parseClock(value: String): Int {
        if (value.isBlank()) return 0
        val parts = value.trim().split(":")
        return runCatching {
            when (parts.size) {
                1 -> parts[0].toInt()
                2 -> parts[0].toInt() * 60 + parts[1].toInt()
                else -> parts[0].toInt() * 3600 + parts[1].toInt() * 60 + parts[2].toInt()
            }
        }.getOrDefault(0)
    }

    // ---------------- Respaldos ----------------

    private fun searchPiped(base: String, query: String): List<MusicTrack> {
        val json = JSONObject(Tls.read("$base/search?q=${encode(query)}&filter=music_songs"))
        val items = json.optJSONArray("items") ?: JSONArray()
        val tracks = ArrayList<MusicTrack>(items.length())
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val raw = item.optString("url")
            val id = raw.substringAfter("v=", "").substringBefore("&")
            if (id.length != 11) continue
            tracks.add(
                MusicTrack(
                    id = id,
                    title = item.optString("title").ifBlank { "Sin título" },
                    uploader = item.optString("uploaderName").ifBlank { "YouTube" },
                    seconds = item.optInt("duration", 0),
                    thumbnail = "https://i.ytimg.com/vi/$id/mqdefault.jpg"
                )
            )
        }
        return tracks
    }

    private fun searchInvidious(base: String, query: String): List<MusicTrack> {
        val array = JSONArray(Tls.read("$base/api/v1/search?q=${encode(query)}&type=video"))
        val tracks = ArrayList<MusicTrack>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("videoId")
            if (id.length != 11) continue
            tracks.add(
                MusicTrack(
                    id = id,
                    title = item.optString("title").ifBlank { "Sin título" },
                    uploader = item.optString("author").ifBlank { "YouTube" },
                    seconds = item.optInt("lengthSeconds", 0),
                    thumbnail = "https://i.ytimg.com/vi/$id/mqdefault.jpg"
                )
            )
        }
        return tracks
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    /** Escapa el texto para meterlo en el JSON del cuerpo de la petición. */
    private fun quote(value: String): String {
        val builder = StringBuilder("\"")
        for (char in value) {
            when (char) {
                '"' -> builder.append("\\\"")
                '\\' -> builder.append("\\\\")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                else -> if (char < ' ') builder.append(" ") else builder.append(char)
            }
        }
        return builder.append('"').toString()
    }
}
