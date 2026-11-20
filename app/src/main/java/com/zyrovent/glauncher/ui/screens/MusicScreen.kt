package com.zyrovent.glauncher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.zyrovent.glauncher.ui.theme.BedrockBar
import com.zyrovent.glauncher.ui.theme.BedrockHighlight
import com.zyrovent.glauncher.ui.theme.BedrockRed
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zyrovent.glauncher.audio.GMusicEngine
import com.zyrovent.glauncher.data.MusicRepository
import com.zyrovent.glauncher.data.MusicTrack
import com.zyrovent.glauncher.data.Playlist
import com.zyrovent.glauncher.data.PlaylistStore
import com.zyrovent.glauncher.ui.components.BedrockWindow
import com.zyrovent.glauncher.ui.components.IconArrowLeft
import com.zyrovent.glauncher.ui.components.IconArrowRight
import com.zyrovent.glauncher.ui.components.IconCheck
import com.zyrovent.glauncher.ui.components.IconClose
import com.zyrovent.glauncher.ui.components.IconList
import com.zyrovent.glauncher.ui.components.IconNote
import com.zyrovent.glauncher.ui.components.IconPause
import com.zyrovent.glauncher.ui.components.IconPlay
import com.zyrovent.glauncher.ui.components.IconPlaylistQueue
import com.zyrovent.glauncher.ui.components.IconSearch
import com.zyrovent.glauncher.ui.components.IconStop
import com.zyrovent.glauncher.ui.components.IconTrash
import com.zyrovent.glauncher.ui.components.IconVolume
import com.zyrovent.glauncher.ui.components.McButton
import com.zyrovent.glauncher.ui.components.McButtonTone
import com.zyrovent.glauncher.ui.components.McTag
import com.zyrovent.glauncher.ui.components.McTextField
import com.zyrovent.glauncher.ui.components.PixelArt
import com.zyrovent.glauncher.ui.components.PixelDivider
import com.zyrovent.glauncher.ui.components.PixelIcon
import com.zyrovent.glauncher.ui.components.PixelText
import com.zyrovent.glauncher.ui.components.RemoteIcon
import com.zyrovent.glauncher.ui.components.SectionLabel
import com.zyrovent.glauncher.ui.theme.BedrockBorder
import com.zyrovent.glauncher.ui.theme.BedrockBorderSoft
import com.zyrovent.glauncher.ui.theme.BedrockGreen
import com.zyrovent.glauncher.ui.theme.BedrockPanel
import com.zyrovent.glauncher.ui.theme.BedrockRow
import com.zyrovent.glauncher.ui.theme.BedrockText
import com.zyrovent.glauncher.ui.theme.BedrockTextDim
import com.zyrovent.glauncher.ui.theme.BedrockVoid
import com.zyrovent.glauncher.ui.theme.DangerRed
import com.zyrovent.glauncher.ui.theme.Gold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MusicUiState(
    val query: String = "",
    val results: List<MusicTrack> = emptyList(),
    val loading: Boolean = false,
    val current: MusicTrack? = null,
    /** Pista cargada en la WebView; se conserva aunque se pulse DETENER. */
    val engineTrack: MusicTrack? = null,
    /** La WebView ya está creada, aunque todavía no tenga canción. */
    val warmUp: Boolean = false,
    /** El motor de audio (WebView invisible) está montado. */
    val audioActive: Boolean = false,
    val paused: Boolean = false,
    /** La WebView avisó de que está cargando (buffering). */
    val buffering: Boolean = false,
    /** Último comando enviado al WebView. */
    val command: String = "",
    val commandSeq: Int = 0,
    val status: String = "",
    val error: String? = null,
    val playlists: List<Playlist> = emptyList(),
    /** Playlist que está sonando; null si viene de los resultados de búsqueda. */
    val activePlaylistId: String? = null
)

class MusicViewModel : ViewModel() {

    private val repository = MusicRepository()

    private val _state = MutableStateFlow(MusicUiState(playlists = PlaylistStore.load()))
    val state: StateFlow<MusicUiState> = _state.asStateFlow()

    init {
        // El reproductor reporta su estado real (cargando, sonando, pausado).
        viewModelScope.launch {
            GMusicEngine.state.collect { report -> applyEngineReport(report) }
        }
    }

    /** Crea la WebView al entrar en GMusic para que el primer play no espere. */
    fun warmUp() {
        if (!_state.value.warmUp) _state.update { it.copy(warmUp = true) }
    }

    fun onQuery(value: String) = _state.update { it.copy(query = value) }

    fun search() {
        val query = _state.value.query.trim()
        if (query.isEmpty()) {
            _state.update { it.copy(error = "Escribe un título o un artista.") }
            return
        }
        _state.update { it.copy(loading = true, error = null, status = "Buscando en YouTube…") }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { repository.search(query) } }
                .onSuccess { tracks ->
                    _state.update {
                        it.copy(
                            loading = false,
                            results = tracks,
                            status = "${tracks.size} resultados",
                            error = if (tracks.isEmpty()) "Sin resultados para \"$query\"." else null
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            loading = false,
                            status = "",
                            error = error.message ?: "No se pudo buscar. Revisa la conexión."
                        )
                    }
                }
        }
    }

    /** Empieza a sonar solo el audio, sin abrir ninguna ventana. */
    fun play(track: MusicTrack, playlistId: String? = null) {
        val sameTrack = _state.value.engineTrack?.id == track.id
        _state.update {
            it.copy(
                current = track,
                engineTrack = track,
                warmUp = true,
                audioActive = true,
                paused = false,
                buffering = !sameTrack,
                command = "play",
                commandSeq = it.commandSeq + 1,
                error = null,
                activePlaylistId = playlistId,
                status = if (sameTrack) "Reanudando \"${track.title}\"" else "Cargando \"${track.title}\"…"
            )
        }
    }

    fun togglePause() {
        if (_state.value.paused) resume() else pause()
    }

    fun pause() {
        _state.update { state ->
            if (state.current == null || !state.audioActive) return@update state
            state.copy(
                paused = true,
                buffering = false,
                command = "pause",
                commandSeq = state.commandSeq + 1,
                status = "En pausa"
            )
        }
    }

    fun resume() {
        _state.update { state ->
            val track = state.current ?: state.engineTrack ?: return@update state
            state.copy(
                current = track,
                audioActive = true,
                paused = false,
                command = "play",
                commandSeq = state.commandSeq + 1,
                status = "Suena \"${track.title}\""
            )
        }
    }

    fun stop() {
        _state.update { state ->
            val wasActive = state.audioActive
            state.copy(
                audioActive = false,
                paused = false,
                buffering = false,
                current = null,
                activePlaylistId = null,
                command = if (wasActive) "pause" else state.command,
                commandSeq = if (wasActive) state.commandSeq + 1 else state.commandSeq,
                status = "Audio detenido"
            )
        }
    }

    /** Cola actual: la playlist activa o, si no, los resultados de búsqueda. */
    private fun queue(): List<MusicTrack> {
        val current = _state.value
        val playlist = current.playlists.firstOrNull { it.id == current.activePlaylistId }
        return playlist?.tracks ?: current.results
    }

    fun skipNext() {
        val list = queue()
        val currentId = _state.value.current?.id
        if (list.isNotEmpty()) {
            val idx = list.indexOfFirst { it.id == currentId }
            val nextTrack = if (idx in 0 until list.size - 1) list[idx + 1] else list.first()
            play(nextTrack, _state.value.activePlaylistId)
        }
    }

    fun skipPrevious() {
        val list = queue()
        val currentId = _state.value.current?.id
        if (list.isNotEmpty()) {
            val idx = list.indexOfFirst { it.id == currentId }
            val prevTrack = if (idx > 0) list[idx - 1] else list.last()
            play(prevTrack, _state.value.activePlaylistId)
        }
    }

    /** Comandos que llegan desde la notificación o los auriculares. */
    fun onExternalCommand(command: String) {
        when (command) {
            "play" -> resume()
            "pause" -> pause()
            "stop" -> stop()
            "next" -> skipNext()
            "previous" -> skipPrevious()
        }
    }

    // ---------------- Playlists ----------------

    fun createPlaylist(name: String): Playlist? {
        val clean = name.trim()
        if (clean.isEmpty()) return null
        val playlist = Playlist("pl_" + System.currentTimeMillis(), clean)
        val list = _state.value.playlists + playlist
        _state.update { it.copy(playlists = list) }
        PlaylistStore.save(list)
        return playlist
    }

    fun deletePlaylist(id: String) {
        val list = _state.value.playlists.filter { it.id != id }
        _state.update {
            it.copy(
                playlists = list,
                activePlaylistId = if (it.activePlaylistId == id) null else it.activePlaylistId
            )
        }
        PlaylistStore.save(list)
    }

    fun addToPlaylist(playlistId: String, track: MusicTrack) {
        val list = _state.value.playlists.map { playlist ->
            if (playlist.id == playlistId && playlist.tracks.none { it.id == track.id }) {
                playlist.copy(tracks = playlist.tracks + track)
            } else {
                playlist
            }
        }
        _state.update { it.copy(playlists = list) }
        PlaylistStore.save(list)
    }

    fun removeFromPlaylist(playlistId: String, trackId: String) {
        val list = _state.value.playlists.map { playlist ->
            if (playlist.id == playlistId) {
                playlist.copy(tracks = playlist.tracks.filterNot { it.id == trackId })
            } else {
                playlist
            }
        }
        _state.update { it.copy(playlists = list) }
        PlaylistStore.save(list)
    }

    fun playPlaylist(playlistId: String) {
        val playlist = _state.value.playlists.firstOrNull { it.id == playlistId } ?: return
        playlist.tracks.firstOrNull()?.let { play(it, playlistId) }
    }

    fun playPlaylistTrack(playlistId: String, track: MusicTrack) = play(track, playlistId)

    /** Traduce lo que cuenta la WebView a estado visible de la interfaz. */
    private fun applyEngineReport(report: String) {
        if (report.isBlank()) return
        _state.update { state ->
            if (!state.audioActive) return@update state
            when (report) {
                "playing" -> state.copy(
                    paused = false,
                    buffering = false,
                    status = "Suena \"${state.current?.title.orEmpty()}\""
                )

                "buffering" -> state.copy(buffering = true, status = "Cargando audio…")
                "paused" -> state.copy(paused = true, buffering = false, status = "En pausa")
                "ended" -> state.copy(paused = true, buffering = false, status = "Canción terminada")
                "blocked" -> state.copy(
                    paused = true,
                    buffering = false,
                    status = "YouTube pidió tocar reproducir: usa REANUDAR"
                )

                "error" -> state.copy(
                    paused = true,
                    buffering = false,
                    error = "No se pudo reproducir esta canción. Prueba con otra."
                )

                else -> state
            }
        }
    }
}

@Composable
fun MusicScreen(vm: MusicViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val current = state.current

    var tab by remember { mutableStateOf(0) } // 0 = resultados, 1 = playlists
    var creatingPlaylist by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var addingTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var expandedPlaylist by remember { mutableStateOf<String?>(null) }

    var currentPage by remember { mutableStateOf(1) }
    var volume by remember { mutableStateOf(1f) }

    // Deja la WebView lista antes del primer play.
    LaunchedEffect(Unit) { vm.warmUp() }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ---- Cuadro Principal GMusic según el boceto de la libreta ----
            BedrockWindow(
                title = "Gmusic",
                subtitle = if (current != null) "Reproduciendo: ${current.title}" else "Tu reproductor Bedrock",
                icon = IconNote,
                modifier = Modifier.fillMaxWidth().weight(1f),
                fillHeight = true,
                trailing = {
                    McTag(
                        when {
                            !state.audioActive -> "DETENIDO"
                            state.buffering -> "CARGANDO"
                            state.paused -> "EN PAUSA"
                            else -> "SONANDO"
                        },
                        when {
                            state.buffering -> Gold
                            state.audioActive && !state.paused -> BedrockGreen
                            else -> BedrockTextDim
                        }
                    )
                }
            ) {
                // Fila Superior: Buscador estilizado con botón [Buscar] y selector de vista
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        McTextField(
                            value = state.query,
                            onValueChange = vm::onQuery,
                            placeholder = "Buscar..."
                        )
                    }
                    McButton(
                        text = if (state.loading) "..." else "Buscar",
                        onClick = vm::search,
                        enabled = !state.loading,
                        icon = IconSearch,
                        tone = McButtonTone.Success,
                        height = 32.dp,
                        modifier = Modifier.width(90.dp)
                    )
                    McButton(
                        text = if (tab == 0) "Listas" else "Canciones",
                        onClick = { tab = if (tab == 0) 1 else 0 },
                        icon = if (tab == 0) IconPlaylistQueue else IconList,
                        tone = McButtonTone.Ghost,
                        height = 32.dp,
                        modifier = Modifier.width(96.dp)
                    )
                }

                Spacer(Modifier.height(4.dp))

                // Lista de resultados / Playlists ocupando todo el ancho central
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (tab == 0) {
                        if (state.results.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                PixelText(
                                    text = if (state.loading) "Buscando en YouTube..." else "Escribe arriba y toca Buscar para encontrar música.",
                                    color = BedrockTextDim,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                itemsIndexed(state.results, key = { _, t -> t.id }) { index, track ->
                                    MusicRow(
                                        index = index + 1,
                                        track = track,
                                        active = current?.id == track.id,
                                        paused = current?.id == track.id && state.paused,
                                        onClick = { vm.play(track) },
                                        onAdd = { addingTrack = track }
                                    )
                                }
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .border(1.dp, BedrockBorderSoft)
                                                .background(BedrockVoid.copy(alpha = 0.6f))
                                                .clickable { if (currentPage > 1) currentPage-- },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            PixelIcon(art = IconArrowLeft, size = 10.dp, tint = BedrockText)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .padding(horizontal = 6.dp)
                                                .border(1.dp, BedrockBorderSoft)
                                                .background(BedrockVoid.copy(alpha = 0.6f))
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            PixelText(
                                                text = "Page $currentPage",
                                                color = BedrockGreen,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .border(1.dp, BedrockBorderSoft)
                                                .background(BedrockVoid.copy(alpha = 0.6f))
                                                .clickable { currentPage++ },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            PixelIcon(art = IconArrowRight, size = 10.dp, tint = BedrockText)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            McButton(
                                text = "+ NUEVA PLAYLIST",
                                onClick = {
                                    newPlaylistName = ""
                                    creatingPlaylist = true
                                },
                                tone = McButtonTone.Success,
                                height = 32.dp,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (state.playlists.isEmpty()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    PixelText(
                                        text = "Aún no tienes listas de reproducción creadas.",
                                        color = BedrockTextDim,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    itemsIndexed(state.playlists, key = { _, p -> p.id }) { _, playlist ->
                                        PlaylistRow(
                                            playlist = playlist,
                                            expanded = expandedPlaylist == playlist.id,
                                            playing = state.activePlaylistId == playlist.id && state.audioActive,
                                            currentId = current?.id,
                                            onToggle = {
                                                expandedPlaylist =
                                                    if (expandedPlaylist == playlist.id) null else playlist.id
                                            },
                                            onPlay = { vm.playPlaylist(playlist.id) },
                                            onDelete = { vm.deletePlaylist(playlist.id) },
                                            onPlayTrack = { vm.playPlaylistTrack(playlist.id, it) },
                                            onRemoveTrack = { vm.removeFromPlaylist(playlist.id, it.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Barra Inferior de Controles del boceto:
                // [Thumbnail] [Nombre de la pista / Smoke it...] [(<) (Play/Pause) (>)] [Volumen Icono] [Barra Volumen] [Cola Icono]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BedrockVoid.copy(alpha = 0.85f))
                        .border(1.dp, BedrockBorderSoft)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .border(1.dp, BedrockBorderSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        RemoteIcon(url = current?.thumbnail, size = 24.dp, fallback = IconNote)
                    }
                    Spacer(Modifier.width(8.dp))
                    PixelText(
                        text = current?.title ?: "Smoke it...",
                        color = if (current != null) BedrockText else BedrockTextDim,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )

                    // Botones de transporte (<) (|| / >) (>)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TransportButton("|<", enabled = state.audioActive, width = 32.dp, onClick = vm::skipPrevious)
                        TransportButton(
                            if (state.paused) ">" else "||",
                            enabled = state.audioActive,
                            width = 38.dp,
                            tone = BedrockGreen,
                            onClick = vm::togglePause
                        )
                        TransportButton(">|", enabled = state.audioActive, width = 32.dp, onClick = vm::skipNext)
                    }

                    Spacer(Modifier.width(10.dp))

                    // Control de Volumen con Altavoz
                    PixelIcon(art = IconVolume, size = 14.dp, tint = BedrockHighlight)
                    Spacer(Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .width(55.dp)
                            .height(6.dp)
                            .border(1.dp, BedrockBorderSoft)
                            .background(BedrockBar)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = volume)
                                .background(BedrockGreen)
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    // Botón de cola / Playlists
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { tab = if (tab == 0) 1 else 0 },
                        contentAlignment = Alignment.Center
                    ) {
                        PixelIcon(art = IconPlaylistQueue, size = 14.dp, tint = BedrockHighlight)
                    }
                }
            }
        }

        // ---- Modal: crear playlist ----
        if (creatingPlaylist) {
            ModalBox(onDismiss = { creatingPlaylist = false }) {
                PixelText("NUEVA PLAYLIST", color = BedrockText, style = MaterialTheme.typography.titleMedium)
                McTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = "Nombre de la lista",
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    McButton(
                        "CANCELAR",
                        onClick = { creatingPlaylist = false },
                        tone = McButtonTone.Ghost,
                        height = 38.dp,
                        modifier = Modifier.weight(1f)
                    )
                    McButton(
                        "CREAR",
                        onClick = {
                            vm.createPlaylist(newPlaylistName)
                            creatingPlaylist = false
                            tab = 1
                        },
                        icon = IconCheck,
                        tone = McButtonTone.Success,
                        height = 38.dp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // ---- Modal: añadir canción a playlist ----
        addingTrack?.let { track ->
            ModalBox(onDismiss = { addingTrack = null }) {
                PixelText(
                    "AÑADIR A PLAYLIST",
                    color = BedrockText,
                    style = MaterialTheme.typography.titleMedium
                )
                PixelText(track.title, color = BedrockTextDim, style = MaterialTheme.typography.bodySmall, maxLines = 2)

                if (state.playlists.isEmpty()) {
                    PixelText(
                        "Todavía no hay playlists. Crea una abajo.",
                        color = BedrockTextDim,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2
                    )
                } else {
                    state.playlists.forEach { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BedrockRow)
                                .border(1.dp, BedrockBorderSoft)
                                .clickable {
                                    vm.addToPlaylist(playlist.id, track)
                                    addingTrack = null
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PixelText(playlist.name, color = BedrockText, modifier = Modifier.weight(1f))
                            PixelText("${playlist.tracks.size}", color = BedrockGreen)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    McButton(
                        "CERRAR",
                        onClick = { addingTrack = null },
                        tone = McButtonTone.Ghost,
                        height = 36.dp,
                        modifier = Modifier.weight(1f)
                    )
                    McButton(
                        "CREAR Y AÑADIR",
                        onClick = {
                            vm.createPlaylist(track.uploader.ifBlank { "Mi lista" })
                                ?.let { vm.addToPlaylist(it.id, track) }
                            addingTrack = null
                            tab = 1
                        },
                        tone = McButtonTone.Success,
                        height = 36.dp,
                        modifier = Modifier.weight(1.4f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ModalBox(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(360.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(BedrockPanel)
                .border(2.dp, BedrockBorder)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            content()
        }
    }
}

/** Botón cuadrado de icono: nunca empuja el texto de al lado. */
@Composable
private fun IconAction(
    icon: PixelArt,
    tint: Color = BedrockText,
    background: Color = BedrockVoid,
    size: Dp = 34.dp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(background.copy(alpha = 0.9f))
            .border(1.dp, BedrockBorderSoft, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        PixelIcon(art = icon, size = size * 0.55f, tint = tint)
    }
}

@Composable
private fun MusicRow(
    index: Int,
    track: MusicTrack,
    active: Boolean,
    paused: Boolean,
    onClick: () -> Unit,
    onAdd: () -> Unit
) {
    val borderColor = if (active) BedrockGreen else BedrockBorderSoft.copy(alpha = 0.5f)

    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(if (active) BedrockRow else BedrockVoid.copy(alpha = 0.5f))
            .border(if (active) 2.dp else 1.dp, borderColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
            PixelText(
                text = index.toString(),
                color = if (active) BedrockGreen else BedrockTextDim,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        RemoteIcon(url = track.thumbnail, size = 40.dp, fallback = IconNote, modifier = Modifier.width(40.dp))
        Box(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            PixelText(
                text = track.title,
                color = if (active) BedrockGreen else BedrockText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
            PixelText(
                text = "${track.uploader} · ${track.durationLabel}",
                color = BedrockTextDim,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
        }
        IconAction(icon = IconPlay, tint = if (active && !paused) BedrockGreen else BedrockText, onClick = onClick)
        Box(Modifier.width(6.dp))
        IconAction(icon = IconNote, tint = Gold, onClick = onAdd)
    }
}

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    expanded: Boolean,
    playing: Boolean,
    currentId: String?,
    onToggle: () -> Unit,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onPlayTrack: (MusicTrack) -> Unit,
    onRemoveTrack: (MusicTrack) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (playing) BedrockRow else BedrockVoid.copy(alpha = 0.45f))
            .border(if (playing) 2.dp else 1.dp, if (playing) BedrockGreen else BedrockBorderSoft)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PixelText(
                text = if (expanded) "▾" else "▸",
                color = BedrockTextDim,
                modifier = Modifier.width(18.dp)
            )
            Column(Modifier.weight(1f)) {
                PixelText(playlist.name, color = BedrockText, maxLines = 1)
                PixelText(
                    text = "${playlist.tracks.size} canciones",
                    color = BedrockTextDim,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconAction(icon = IconPlay, tint = BedrockGreen, onClick = onPlay)
            Box(Modifier.width(6.dp))
            IconAction(icon = IconTrash, tint = DangerRed, onClick = onDelete)
        }

        if (expanded) {
            if (playlist.tracks.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(10.dp)) {
                    PixelText(
                        "Playlist vacía: añade canciones con el botón + de los resultados.",
                        color = BedrockTextDim,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2
                    )
                }
            } else {
                playlist.tracks.forEachIndexed { index, track ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 22.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PixelText(
                            text = "${index + 1}",
                            color = BedrockTextDim,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(20.dp)
                        )
                        Column(
                            Modifier
                                .weight(1f)
                                .clickable { onPlayTrack(track) }
                        ) {
                            PixelText(
                                text = track.title,
                                color = if (track.id == currentId) BedrockGreen else BedrockText,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1
                            )
                            PixelText(
                                text = track.uploader,
                                color = BedrockTextDim,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1
                            )
                        }
                        IconAction(icon = IconClose, tint = BedrockTextDim, size = 28.dp, onClick = { onRemoveTrack(track) })
                    }
                }
            }
        }
    }
}

/** Botón de transporte del reproductor: ancho fijo y texto centrado. */
@Composable
private fun TransportButton(
    label: String,
    enabled: Boolean = true,
    width: Dp = 46.dp,
    tone: Color = BedrockText,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(38.dp)
            .background(if (enabled) BedrockRow else BedrockPanel)
            .border(1.dp, BedrockBorderSoft)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        PixelText(
            text = label,
            color = if (enabled) tone else BedrockTextDim,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
