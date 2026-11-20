package com.zyrovent.glauncher.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zyrovent.glauncher.data.AuthApi
import com.zyrovent.glauncher.data.ChatMessage
import com.zyrovent.glauncher.data.ChatUser
import com.zyrovent.glauncher.data.FriendsBundle
import com.zyrovent.glauncher.data.Gif
import com.zyrovent.glauncher.data.GiphyApi
import com.zyrovent.glauncher.data.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Estado del modal de login (dos pasos, igual que la web). */
data class LoginUiState(
    val open: Boolean = false,
    val username: String = "",
    val password: String = "",
    val securityCode: String = "",
    val busy: Boolean = false,
    val error: String? = null
)

/** Estado de la cuenta con sesión iniciada. */
data class AccountUiState(
    val loading: Boolean = false,
    val profile: org.json.JSONObject? = null,
    val error: String? = null
)

/** Login de GLauncher en dos pasos con token guardado en [SessionStore]. */
class SessionViewModel(application: Application) : AndroidViewModel(application) {

    val store = SessionStore(application)

    private val _login = MutableStateFlow(LoginUiState())
    val login: StateFlow<LoginUiState> = _login.asStateFlow()

    private val _account = MutableStateFlow(AccountUiState())
    val account: StateFlow<AccountUiState> = _account.asStateFlow()

    val session get() = store.session

    fun openLogin() = _login.update { LoginUiState(open = true, username = it.username) }
    fun closeLogin() = _login.update { LoginUiState() }

    fun onUsername(value: String) = _login.update { it.copy(username = value, error = null) }
    fun onPassword(value: String) = _login.update { it.copy(password = value, error = null) }
    fun onSecurityCode(value: String) =
        _login.update { it.copy(securityCode = value.filter { it.isDigit() }.take(6), error = null) }

    /** Un solo paso: usuario, contraseña y código de seguridad juntos. */
    fun submitLogin() {
        val state = _login.value
        val username = state.username.trim()
        if (username.isEmpty() || state.password.isEmpty()) {
            _login.update { it.copy(error = "Escribe tu usuario y tu contraseña.") }
            return
        }
        if (state.securityCode.length != 6) {
            _login.update { it.copy(error = "El código de seguridad debe tener 6 dígitos.") }
            return
        }
        _login.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val token = AuthApi.login(username, state.password, state.securityCode)
                    val profile = AuthApi.userInfo(token)
                    store.save(token, profile)
                    profile
                }
            }.onSuccess { profile ->
                _account.update { it.copy(profile = profile, error = null) }
                _login.value = LoginUiState()
            }.onFailure { error ->
                _login.update {
                    it.copy(busy = false, error = error.message ?: "Error en el inicio de sesión.")
                }
            }
        }
    }

    fun logout() {
        store.logout()
        _account.value = AccountUiState()
        _login.value = LoginUiState()
    }

    /** Refresca el perfil con el token guardado. */
    fun loadProfile() {
        val token = store.token ?: return
        _account.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { AuthApi.userInfo(token) } }
                .onSuccess { profile ->
                    store.save(token, profile)
                    _account.update { it.copy(loading = false, profile = profile) }
                }
                .onFailure { error ->
                    val msg = error.message ?: ""
                    if (msg.contains("401") || msg.contains("Unauthorized") || msg.contains("invalido")) {
                        store.logout()
                        _account.update { it.copy(loading = false, profile = null, error = "Sesión expirada. Inicia sesión nuevamente.") }
                    } else {
                        _account.update {
                            it.copy(loading = false, error = error.message ?: "No se pudo cargar el perfil.")
                        }
                    }
                }
        }
    }

    init {
        if (store.hasSession()) loadProfile()
    }
}

/** Estado del GChat. */
data class ChatUiState(
    val bundle: FriendsBundle = FriendsBundle(),
    val query: String = "",
    val searchResults: List<ChatUser> = emptyList(),
    val searching: Boolean = false,
    val active: ChatUser? = null,
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val sending: Boolean = false,
    val status: String = "",
    val error: String? = null,
    /** Mensaje al que se está respondiendo, si hay alguno. */
    val replyTo: ChatMessage? = null,
    /** Selector de GIFs desplegado bajo la conversación. */
    val gifOpen: Boolean = false,
    val gifQuery: String = "",
    val gifs: List<Gif> = emptyList(),
    val gifLoading: Boolean = false,
    /** Id propio del usuario, para decidir de qué lado va cada burbuja. */
    val myId: String? = null
) {
    /** Nombre visible del autor de un mensaje dentro de la conversación. */
    fun authorName(message: ChatMessage): String =
        if (active != null && message.senderId == active.id) active.username else "Tú"
}

/** GChat: amigos, búsqueda, conversación activa y refresco cada 5 s. */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SessionStore(application)

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var pollJob: Job? = null
    private var gifJob: Job? = null

    init {
        _state.update { it.copy(myId = store.session.value?.userId) }
        refreshFriends()
    }

    fun onQuery(value: String) = _state.update { it.copy(query = value, error = null) }
    fun onDraft(value: String) = _state.update { it.copy(draft = value) }

    fun refreshFriends() {
        val token = store.token ?: return
        _state.update { it.copy(status = "Cargando amigos…") }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { AuthApi.friends(token) } }
                .onSuccess { bundle ->
                    _state.update { it.copy(bundle = bundle, status = "", error = null) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(status = "", error = error.message ?: "No se pudieron cargar los amigos.")
                    }
                }
        }
    }

    fun search() {
        val query = _state.value.query.trim()
        if (query.isEmpty()) return
        val token = store.token
        _state.update { it.copy(searching = true, error = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { AuthApi.searchUsers(query, token) } }
                .onSuccess { users ->
                    _state.update {
                        it.copy(
                            searching = false,
                            searchResults = users.filter { user ->
                                !user.username.equals(query, ignoreCase = true)
                            }
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(searching = false, error = error.message ?: "Sin resultados.") }
                }
        }
    }

    fun clearSearch() = _state.update { it.copy(searchResults = emptyList(), query = "") }

    fun addFriend(username: String) {
        val token = store.token ?: return
        _state.update { it.copy(status = "Enviando solicitud…", error = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { AuthApi.addFriend(token, username) } }
                .onSuccess { message ->
                    _state.update {
                        it.copy(status = message.ifBlank { "Solicitud enviada." }, searchResults = emptyList())
                    }
                    refreshFriends()
                }
                .onFailure { error ->
                    _state.update { it.copy(status = "", error = error.message ?: "No se pudo enviar.") }
                }
        }
    }

    fun acceptFriend(id: String) = friendAction(id) { AuthApi.acceptFriend(it, id) }

    fun removeFriend(id: String) = friendAction(id) { AuthApi.removeFriend(it, id) }

    private fun friendAction(id: String, action: (String) -> String) {
        val token = store.token ?: return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { action(token) } }
                .onSuccess { message ->
                    _state.update {
                        it.copy(
                            status = message.ifBlank { "Listo." },
                            active = if (it.active?.id == id) null else it.active
                        )
                    }
                    refreshFriends()
                }
                .onFailure { error ->
                    _state.update { it.copy(error = error.message ?: "No se pudo completar la acción.") }
                }
        }
    }

    fun openChat(user: ChatUser) {
        _state.update {
            it.copy(active = user, messages = emptyList(), draft = "", replyTo = null, error = null)
        }
        loadHistory()
        startPolling()
    }

    fun closeChat() {
        pollJob?.cancel()
        pollJob = null
        _state.update { it.copy(active = null, messages = emptyList(), draft = "", replyTo = null) }
    }

    /** Marca un mensaje como destinatario de la respuesta. */
    fun setReply(message: ChatMessage) = _state.update { it.copy(replyTo = message) }

    fun clearReply() = _state.update { it.copy(replyTo = null) }

    fun send() {
        val content = _state.value.draft.trim()
        if (content.isEmpty()) return
        sendContent(content, clearDraft = true)
    }

    /** Envía un GIF: el contenido es la URL y se guarda como cualquier mensaje. */
    fun sendGif(url: String) {
        if (url.isBlank()) return
        sendContent(url, clearDraft = false)
    }

    private fun sendContent(content: String, clearDraft: Boolean) {
        val token = store.token ?: return
        val target = _state.value.active ?: return
        if (_state.value.sending) return
        // La API guarda el nombre de quien responde, no su id.
        val replyName = _state.value.replyTo?.let { message ->
            if (message.senderId == target.id) target.username
            else store.session.value?.username?.takeIf { it.isNotBlank() } ?: "Tú"
        }

        _state.update {
            it.copy(
                draft = if (clearDraft) "" else it.draft,
                sending = true,
                error = null,
                replyTo = null,
                gifOpen = false
            )
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { AuthApi.send(token, target.id, content, replyName) }
            }
                .onSuccess { sent ->
                    _state.update { state ->
                        val known = sent?.id != null && state.messages.any { it.id == sent.id }
                        // Si la API no dio nuestro id en el perfil, se aprende del
                        // mensaje recién enviado (nunca del id del amigo).
                        val learned = sent?.senderId
                            ?.takeIf { it.isNotBlank() && it != target.id }
                        state.copy(
                            sending = false,
                            myId = state.myId ?: learned,
                            messages = if (sent == null || known) state.messages else state.messages + sent
                        )
                    }
                    sent?.senderId?.takeIf { it.isNotBlank() && it != target.id }?.let(store::rememberSelfId)
                    loadHistory()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            sending = false,
                            draft = if (clearDraft) content else it.draft,
                            error = error.message ?: "No se pudo enviar."
                        )
                    }
                }
        }
    }

    fun toggleGifs() {
        val opening = !_state.value.gifOpen
        _state.update { it.copy(gifOpen = opening, error = null) }
        if (opening && _state.value.gifs.isEmpty()) loadGifs(_state.value.gifQuery)
    }

    fun closeGifs() = _state.update { it.copy(gifOpen = false) }

    fun onGifQuery(value: String) {
        _state.update { it.copy(gifQuery = value) }
        loadGifs(value)
    }

    /** Giphy: tendencias si no hay texto y búsqueda con 320 ms de espera. */
    private fun loadGifs(query: String) {
        gifJob?.cancel()
        gifJob = viewModelScope.launch {
            _state.update { it.copy(gifLoading = true) }
            if (query.isNotBlank()) delay(320)
            runCatching { withContext(Dispatchers.IO) { GiphyApi.search(query) } }
                .onSuccess { gifs ->
                    _state.update { it.copy(gifs = gifs, gifLoading = false, error = null) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            gifLoading = false,
                            error = error.message ?: "No se pudieron cargar los GIFs."
                        )
                    }
                }
        }
    }

    private fun loadHistory() {
        val token = store.token ?: return
        val target = _state.value.active ?: return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { AuthApi.history(token, target.id) } }
                .onSuccess { history -> _state.update { it.copy(messages = history, error = null) } }
                .onFailure { error ->
                    _state.update { it.copy(error = error.message ?: "No se pudo cargar el historial.") }
                }
        }
    }

    /** Refresco como el de la web: historial cada 5 s mientras está la charla abierta. */
    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                delay(5_000)
                if (_state.value.active == null) break
                loadHistory()
            }
        }
    }

    override fun onCleared() {
        pollJob?.cancel()
        gifJob?.cancel()
        super.onCleared()
    }
}