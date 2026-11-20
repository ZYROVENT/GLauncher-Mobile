package com.zyrovent.glauncher.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/** Usuario del GChat (amigo o resultado de búsqueda). */
data class ChatUser(
    val id: String,
    val username: String,
    val role: String = "Jugador",
    val avatarUrl: String? = null,
    val status: String = "Disponible"
)

/** Mensaje del GChat. */
data class ChatMessage(
    val id: String?,
    val senderId: String,
    val content: String,
    val replyToUsername: String? = null
) {
    val isGif: Boolean
        get() = content.startsWith("http") && content.contains("giphy.com")
}

/** Amigos, solicitudes pendientes y enviadas. */
data class FriendsBundle(
    val friends: List<ChatUser> = emptyList(),
    val pending: List<ChatUser> = emptyList(),
    val sent: List<ChatUser> = emptyList()
)

/**
 * Cliente de `glauncher-api.onrender.com`: los mismos endpoints que usa la web
 * (login de dos pasos, amigos y GChat). Todo con TLS 1.2 y `Authorization: Bearer`.
 */
object AuthApi {

    private const val BASE = "https://glauncher-api.onrender.com"

    /** Paso 1: comprueba usuario y contraseña. */
    fun checkCredentials(username: String, password: String) {
        val body = JSONObject().put("username", username).put("password", password).toString()
        Tls.postJson("$BASE/api/auth/check_credentials", body, token = null)
    }

    /** Paso 2: añade el código de seguridad y devuelve el token. */
    fun login(username: String, password: String, securityCode: String): String {
        val body = JSONObject()
            .put("username", username)
            .put("password", password)
            .put("security_code", securityCode)
            .toString()
        val response = JSONObject(Tls.postJson("$BASE/api/auth/login", body, token = null))
        return response.textOrNull("token")
            ?: throw IOException(response.textOr("message", "No se pudo iniciar sesión."))
    }

    /** Perfil del usuario autenticado. */
    fun userInfo(token: String): JSONObject =
        JSONObject(Tls.read("$BASE/api/user_info", token = token))

    /** Amigos agrupados como los devuelve la web. */
    fun friends(token: String): FriendsBundle {
        val root = JSONObject(Tls.read("$BASE/api/friends", token = token))
        return FriendsBundle(
            friends = parseUsers(root.optJSONArray("friends")),
            pending = parseUsers(root.optJSONArray("pending")),
            sent = parseUsers(root.optJSONArray("sent"))
        )
    }

    /** Búsqueda de usuarios por nombre. */
    fun searchUsers(query: String, token: String?): List<ChatUser> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val array = JSONArray(Tls.read("$BASE/api/users/search?q=$encoded", token = token))
        return parseUsers(array)
    }

    fun addFriend(token: String, username: String): String {
        val body = JSONObject().put("username", username).toString()
        return messageOf(Tls.postJson("$BASE/api/friends/add", body, token = token))
    }

    fun acceptFriend(token: String, friendId: String): String {
        val body = JSONObject().put("friend_id", friendId).toString()
        return messageOf(Tls.postJson("$BASE/api/friends/accept", body, token = token))
    }

    fun removeFriend(token: String, friendId: String): String {
        val body = JSONObject().put("friend_id", friendId).toString()
        return messageOf(Tls.postJson("$BASE/api/friends/remove", body, token = token))
    }

    /** Historial de la conversación con un amigo. */
    fun history(token: String, friendId: String): List<ChatMessage> {
        val array = JSONArray(Tls.read("$BASE/api/gchat/history/$friendId", token = token))
        val messages = ArrayList<ChatMessage>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val content = item.textOrNull("content") ?: item.textOrNull("message") ?: continue
            messages.add(
                ChatMessage(
                    id = item.textOrNull("id"),
                    senderId = item.textOrNull("sender_id") ?: "",
                    content = content,
                    replyToUsername = item.textOrNull("reply_to_username")
                )
            )
        }
        return messages
    }

    /**
     * Envía un mensaje (texto o URL de GIF) y devuelve el mensaje guardado.
     * Si [replyToUsername] no es nulo, el servidor lo marca como respuesta.
     */
    fun send(
        token: String,
        recipientId: String,
        content: String,
        replyToUsername: String? = null
    ): ChatMessage? {
        val payload = JSONObject().put("content", content)
        if (!replyToUsername.isNullOrBlank()) payload.put("reply_to_username", replyToUsername)
        val root = JSONObject(
            Tls.postJson("$BASE/api/gchat/send/$recipientId", payload.toString(), token = token)
        )
        val text = root.textOrNull("content") ?: root.textOrNull("message") ?: return null
        return ChatMessage(
            id = root.textOrNull("id"),
            senderId = root.textOrNull("sender_id") ?: "",
            content = text,
            replyToUsername = root.textOrNull("reply_to_username") ?: replyToUsername
        )
    }

    private fun messageOf(response: String): String =
        runCatching { JSONObject(response).optString("message") }.getOrDefault("Listo.")

    private fun parseUsers(array: JSONArray?): List<ChatUser> {
        if (array == null) return emptyList()
        val users = ArrayList<ChatUser>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.textOrNull("id") ?: item.textOrNull("user_id") ?: continue
            val username = item.textOrNull("username") ?: continue
            users.add(
                ChatUser(
                    id = id,
                    username = username,
                    role = item.textOr("role", "Jugador"),
                    avatarUrl = item.textOrNull("avatar_url"),
                    status = item.textOr("status", "Disponible")
                )
            )
        }
        return users
    }
}