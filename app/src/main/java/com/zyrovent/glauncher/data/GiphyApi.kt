package com.zyrovent.glauncher.data

import org.json.JSONArray
import java.net.URLEncoder

/** GIF del catálogo de Giphy: vista previa ligera y el archivo que se envía. */
data class Gif(
    val id: String,
    val title: String,
    val previewUrl: String,
    val url: String
)

/**
 * Giphy para el GChat. Se usa la clave pública de Giphy; si algún día se agota,
 * solo hay que cambiarla aquí (también se puede sobreescribir con [useKey]).
 */
object GiphyApi {

    /** Clave de Giphy. Sustitúyela por la tuya desde https://developers.giphy.com */
    private const val API_KEY = "Gc7131jiJuvI7IdN0HZ1D7nh0ow5BU6g"

    private const val BASE = "https://api.giphy.com/v1/gifs"
    private const val RATING = "pg-13"

    private var key: String = API_KEY

    fun useKey(value: String) {
        if (value.isNotBlank()) key = value.trim()
    }

    /** GIFs en tendencia para abrir el selector sin escribir nada. */
    fun trending(limit: Int = 24): List<Gif> = request("$BASE/trending?api_key=$key&limit=$limit&rating=$RATING")

    /** Búsqueda por texto. Sin consulta devuelve las tendencias. */
    fun search(query: String, limit: Int = 24): List<Gif> {
        val clean = query.trim()
        if (clean.isEmpty()) return trending(limit)
        val encoded = URLEncoder.encode(clean, "UTF-8")
        return request("$BASE/search?api_key=$key&q=$encoded&limit=$limit&rating=$RATING")
    }

    private fun request(url: String): List<Gif> {
        val root = org.json.JSONObject(Tls.read(url, timeoutMs = 15_000))
        val data = root.optJSONArray("data") ?: return emptyList()
        return parse(data)
    }

    private fun parse(array: JSONArray): List<Gif> {
        val gifs = ArrayList<Gif>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val images = item.optJSONObject("images") ?: continue
            // fixed_width: ~200 px de ancho, el equilibrio entre peso y nitidez.
            val full = images.optJSONObject("fixed_width")?.optString("url").orEmpty()
                .ifBlank { images.optJSONObject("downsized")?.optString("url").orEmpty() }
            if (full.isBlank()) continue
            val preview = images.optJSONObject("preview_gif")?.optString("url").orEmpty()
                .ifBlank { images.optJSONObject("fixed_width_small")?.optString("url").orEmpty() }
                .ifBlank { full }
            gifs.add(
                Gif(
                    id = item.optString("id").ifBlank { full },
                    title = item.optString("title").ifBlank { "GIF" },
                    previewUrl = preview,
                    url = full
                )
            )
        }
        return gifs
    }
}
