package com.zyrovent.glauncher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** Sesión de GLauncher (web) guardada en el dispositivo. */
data class AuthSession(
    val token: String,
    val username: String,
    val role: String = "Jugador",
    val gcoins: Int = 0,
    val avatarUrl: String? = null,
    /** Id del usuario en la API: sirve para saber qué mensajes del chat son tuyos. */
    val userId: String? = null
)

/**
 * Persiste el token de `glauncher-api` y el perfil devuelto por `/api/user_info`.
 * Es reactivo para que Cuentas y GChat se actualicen al entrar o salir.
 */
class SessionStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("glauncher_session", Context.MODE_PRIVATE)

    private val _session = MutableStateFlow(read())
    val session: StateFlow<AuthSession?> = _session.asStateFlow()

    val token: String? get() = prefs.getString(KEY_TOKEN, null)

    fun hasSession(): Boolean = !token.isNullOrBlank()

    /** Guarda el token y, si llega el perfil, también sus datos. */
    fun save(token: String, profile: JSONObject? = null) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
        if (profile != null) {
            prefs.edit()
                .putString(KEY_USERNAME, profile.textOr("username", prefs.getString(KEY_USERNAME, "") ?: ""))
                .putString(KEY_ROLE, profile.textOr("role", "Jugador"))
                .putInt(KEY_GCOINS, profile.optInt("gcoins", 0))
                .putString(KEY_AVATAR, profile.textOrNull("avatar_url"))
                .putString(
                    KEY_USER_ID,
                    profile.textOrNull("id")
                        ?: profile.textOrNull("user_id")
                        ?: profile.textOrNull("_id")
                        ?: prefs.getString(KEY_USER_ID, null)
                )
                .apply()
        }
        _session.value = read()
    }

    /**
     * Guarda el id propio la primera vez que el chat ve un mensaje enviado por
     * nosotros (el perfil no siempre lo trae). Sirve para alinear burbujas.
     */
    fun rememberSelfId(id: String) {
        val clean = id.trim()
        if (clean.isEmpty() || clean == "null") return
        if (prefs.getString(KEY_USER_ID, null) == clean) return
        prefs.edit().putString(KEY_USER_ID, clean).apply()
        _session.value = read()
    }

    fun logout() {
        prefs.edit().clear().apply()
        _session.value = null
    }

    private fun read(): AuthSession? {
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        return AuthSession(
            token = token,
            username = prefs.getString(KEY_USERNAME, "") ?: "",
            role = prefs.getString(KEY_ROLE, "Jugador") ?: "Jugador",
            gcoins = prefs.getInt(KEY_GCOINS, 0),
            avatarUrl = prefs.getString(KEY_AVATAR, null),
            userId = prefs.getString(KEY_USER_ID, null)
        )
    }

    private companion object {
        const val KEY_TOKEN = "token"
        const val KEY_USERNAME = "username"
        const val KEY_ROLE = "role"
        const val KEY_GCOINS = "gcoins"
        const val KEY_AVATAR = "avatar"
        const val KEY_USER_ID = "user_id"
    }
}