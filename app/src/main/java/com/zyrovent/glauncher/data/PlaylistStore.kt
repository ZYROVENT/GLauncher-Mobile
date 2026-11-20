package com.zyrovent.glauncher.data

import com.zyrovent.glauncher.LauncherApp
import org.json.JSONArray
import org.json.JSONObject

/** Una lista de reproducción de GMusic. */
data class Playlist(
    val id: String,
    val name: String,
    val tracks: List<MusicTrack> = emptyList()
)

/**
 * Guarda las playlists de GMusic en SharedPreferences como JSON. Se guardan las
 * pistas completas (título, canal, miniatura) para poder reproducirlas sin
 * volver a buscar en YouTube.
 */
object PlaylistStore {

    private const val PREFS = "gmusic_playlists"
    private const val KEY = "playlists"

    private fun prefs() = LauncherApp.instance.getSharedPreferences(PREFS, 0)

    fun load(): List<Playlist> {
        val raw = prefs().getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val tracks = obj.optJSONArray("tracks") ?: JSONArray()
                    add(
                        Playlist(
                            id = obj.optString("id"),
                            name = obj.optString("name"),
                            tracks = buildList {
                                for (j in 0 until tracks.length()) {
                                    val t = tracks.getJSONObject(j)
                                    add(
                                        MusicTrack(
                                            id = t.optString("id"),
                                            title = t.optString("title"),
                                            uploader = t.optString("uploader"),
                                            seconds = t.optInt("seconds"),
                                            thumbnail = t.optString("thumbnail").takeIf { it.isNotBlank() }
                                        )
                                    )
                                }
                            }
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(playlists: List<Playlist>) {
        val array = JSONArray()
        playlists.forEach { playlist ->
            val tracks = JSONArray()
            playlist.tracks.forEach { track ->
                tracks.put(
                    JSONObject()
                        .put("id", track.id)
                        .put("title", track.title)
                        .put("uploader", track.uploader)
                        .put("seconds", track.seconds)
                        .put("thumbnail", track.thumbnail ?: "")
                )
            }
            array.put(
                JSONObject()
                    .put("id", playlist.id)
                    .put("name", playlist.name)
                    .put("tracks", tracks)
            )
        }
        prefs().edit().putString(KEY, array.toString()).apply()
    }
}
