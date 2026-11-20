package com.zyrovent.glauncher.ui.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zyrovent.glauncher.data.GamePaths
import com.zyrovent.glauncher.data.LauncherStore
import com.zyrovent.glauncher.data.ModCategory
import com.zyrovent.glauncher.data.ModHit
import com.zyrovent.glauncher.data.ModsRepository
import com.zyrovent.glauncher.ui.components.BedrockTab
import com.zyrovent.glauncher.ui.components.BedrockWindow
import com.zyrovent.glauncher.ui.components.IconBlock
import com.zyrovent.glauncher.ui.components.IconDownload
import com.zyrovent.glauncher.ui.components.IconStop
import com.zyrovent.glauncher.ui.components.IconFolder
import com.zyrovent.glauncher.ui.components.IconSearch
import com.zyrovent.glauncher.ui.components.PixelIcon
import com.zyrovent.glauncher.ui.components.RemoteIcon
import com.zyrovent.glauncher.ui.components.McButton
import com.zyrovent.glauncher.ui.components.McButtonTone
import com.zyrovent.glauncher.ui.components.McTag
import com.zyrovent.glauncher.ui.components.McTextField
import com.zyrovent.glauncher.ui.components.PixelDivider
import com.zyrovent.glauncher.ui.components.PixelText
import com.zyrovent.glauncher.ui.components.SectionLabel
import com.zyrovent.glauncher.ui.theme.BedrockBorderSoft
import com.zyrovent.glauncher.ui.theme.BedrockGreen
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

data class InstalledMod(
    val file: java.io.File,
    val name: String,
    val sizeKb: Long,
    val enabled: Boolean
)

data class ModsUiState(
    val query: String = "",
    val categories: List<ModCategory> = emptyList(),
    val selected: ModCategory? = null,
    val results: List<ModHit> = emptyList(),
    val installedMods: List<InstalledMod> = emptyList(),
    val activeTab: Int = 0, // 0 = Catálogo (Modrinth), 1 = Gestor Local (Instalados)
    val loading: Boolean = false,
    val downloadingId: String? = null,
    val progress: Float = 0f,
    val gameVersion: String = "",
    val status: String = "",
    val error: String? = null
)

class ModsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ModsRepository()
    private val paths = GamePaths(application)
    private val store = LauncherStore(application)

    private val _state = MutableStateFlow(
        ModsUiState(
            categories = repository.categories,
            selected = repository.categories.first(),
            gameVersion = store.settings().selectedVersion
        )
    )
    val state: StateFlow<ModsUiState> = _state.asStateFlow()

    init {
        loadInstalled()
    }

    fun onQuery(value: String) = _state.update { it.copy(query = value) }

    fun setTab(index: Int) {
        _state.update { it.copy(activeTab = index) }
        if (index == 1) loadInstalled()
    }

    fun selectCategory(category: ModCategory) {
        _state.update { it.copy(selected = category, results = emptyList(), error = null, status = "") }
        loadInstalled()
    }

    fun loadInstalled() {
        val currentVersion = _state.value.gameVersion.ifBlank { "1.18" }
        val category = _state.value.selected
        val folderName = category?.folder ?: "mods"
        val targetDir = paths.instanceDir(currentVersion).resolve(folderName)

        viewModelScope.launch(Dispatchers.IO) {
            val list = if (targetDir.exists() && targetDir.isDirectory) {
                targetDir.listFiles()?.filter { it.isFile && (it.extension == "jar" || it.extension == "disabled" || it.extension == "zip") }
                    ?.map { file ->
                        InstalledMod(
                            file = file,
                            name = file.nameWithoutExtension,
                            sizeKb = file.length() / 1024,
                            enabled = !file.name.endsWith(".disabled")
                        )
                    }?.sortedBy { it.name.lowercase() } ?: emptyList()
            } else {
                emptyList()
            }
            withContext(Dispatchers.Main) {
                _state.update { it.copy(installedMods = list) }
            }
        }
    }

    fun toggleMod(mod: InstalledMod) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = mod.file
            val newFile = if (mod.enabled) {
                java.io.File(file.parentFile, "${file.name}.disabled")
            } else {
                java.io.File(file.parentFile, file.name.removeSuffix(".disabled"))
            }
            file.renameTo(newFile)
            loadInstalled()
        }
    }

    fun deleteMod(mod: InstalledMod) {
        viewModelScope.launch(Dispatchers.IO) {
            mod.file.delete()
            loadInstalled()
        }
    }

    fun search() {
        val current = _state.value
        val category = current.selected ?: return
        _state.update { it.copy(loading = true, error = null, status = "Consultando Modrinth…") }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { repository.search(current.query, category) }
            }.onSuccess { hits ->
                _state.update {
                    it.copy(
                        loading = false,
                        results = hits,
                        status = if (hits.isEmpty()) "" else "${hits.size} proyectos",
                        error = if (hits.isEmpty()) "Sin resultados en ${category.label}." else null
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        loading = false,
                        status = "",
                        error = error.message ?: "No se pudo consultar Modrinth."
                    )
                }
            }
        }
    }

    fun download(hit: ModHit) {
        if (_state.value.downloadingId != null) return
        val version = _state.value.gameVersion
        _state.update {
            it.copy(downloadingId = hit.id, progress = 0f, error = null, status = "Buscando archivo para $version…")
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val file = repository.latestFile(hit.id, version)
                        ?: error("${hit.title} no tiene archivo para Minecraft $version.")
                    val folder = folderFor(hit.type)
                    var lastReported = -1
                    val saved = repository.download(file, paths.instanceDir(version).resolve(folder)) { progress ->
                        val percent = (progress * 100).toInt()
                        if (percent != lastReported) {
                            lastReported = percent
                            _state.update { it.copy(progress = progress) }
                        }
                    }
                    folder to saved.name
                }
            }.onSuccess { (folder, name) ->
                _state.update {
                    it.copy(
                        downloadingId = null,
                        progress = 1f,
                        status = "Guardado en $folder/$name"
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        downloadingId = null,
                        error = error.message ?: "No se pudo descargar.",
                        status = ""
                    )
                }
            }
        }
    }

    private fun folderFor(type: String): String = when (type) {
        "resourcepack" -> "resourcepacks"
        "shader" -> "shaderpacks"
        "modpack" -> "modpacks"
        else -> "mods"
    }
}

@Composable
fun ModsScreen(vm: ModsViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val category = state.selected

    Row(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ---- Columna izquierda: Filtros, Categorías y Rutas ----
        Column(Modifier.fillMaxHeight().weight(0.35f)) {
            BedrockWindow(
                title = "Mods & Addons",
                subtitle = "Minecraft ${state.gameVersion.ifBlank { "1.18" }}",
                icon = IconBlock,
                modifier = Modifier.fillMaxWidth().weight(1f),
                fillHeight = true,
                contentModifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                trailing = { McTag("MODRINTH", BedrockGreen) }
            ) {
                // Selector de pestañas: Catálogo vs Instalados
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    McButton(
                        text = "EXPLORAR",
                        onClick = { vm.setTab(0) },
                        tone = if (state.activeTab == 0) McButtonTone.Success else McButtonTone.Ghost,
                        modifier = Modifier.weight(1f),
                        height = 36.dp
                    )
                    McButton(
                        text = "GESTIONAR",
                        onClick = { vm.setTab(1) },
                        tone = if (state.activeTab == 1) McButtonTone.Success else McButtonTone.Ghost,
                        modifier = Modifier.weight(1f),
                        height = 36.dp
                    )
                }

                PixelDivider()

                if (state.activeTab == 0) {
                    McTextField(
                        value = state.query,
                        onValueChange = vm::onQuery,
                        placeholder = "Buscar en Modrinth..."
                    )
                }

                SectionLabel("Categorías")
                state.categories.forEach { item ->
                    BedrockTab(
                        text = item.label,
                        selected = item == category,
                        onClick = { vm.selectCategory(item) }
                    )
                }

                if (state.activeTab == 0) {
                    McButton(
                        text = if (state.loading) "BUSCANDO…" else "BUSCAR EN LÍNEA",
                        onClick = vm::search,
                        enabled = !state.loading && state.downloadingId == null,
                        icon = IconSearch,
                        tone = McButtonTone.Success,
                        modifier = Modifier.fillMaxWidth(),
                        height = 38.dp
                    )
                }

                PixelDivider()
                SectionLabel("Directorio destino")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BedrockVoid.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    PixelText(
                        text = "instances/${state.gameVersion.ifBlank { "1.18" }}/${category?.folder ?: "mods"}/",
                        color = BedrockGreen,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }

                if (state.downloadingId != null) {
                    SectionLabel("Descargando")
                    PixelText(
                        text = "${(state.progress * 100).toInt()}% completado",
                        color = Gold,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                if (state.status.isNotBlank()) {
                    PixelText(
                        text = state.status,
                        color = BedrockGreen,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2
                    )
                }
                state.error?.let { message ->
                    PixelText(
                        text = message,
                        color = DangerRed,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3
                    )
                }
            }
        }

        // ---- Columna derecha: Catálogo / Gestor Local ----
        Column(Modifier.fillMaxHeight().weight(0.65f)) {
            BedrockWindow(
                title = if (state.activeTab == 0) "Catálogo Modrinth" else "Archivos Instalados",
                subtitle = if (state.activeTab == 0) {
                    if (state.results.isEmpty()) "Busca para ver contenido" else "${state.results.size} resultados"
                } else {
                    "${state.installedMods.size} archivos en ${category?.folder ?: "mods"}"
                },
                icon = IconFolder,
                modifier = Modifier.fillMaxWidth().weight(1f),
                fillHeight = true
            ) {
                if (state.activeTab == 0) {
                    // Vista de Catálogo Modrinth
                    if (state.results.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                PixelText(
                                    text = "Explora miles de Mods, Texturas y Shaders",
                                    color = BedrockText,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                PixelText(
                                    text = "Elige una categoría a la izquierda y pulsa BUSCAR EN LÍNEA.",
                                    color = BedrockTextDim,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(bottom = 8.dp)
                        ) {
                            itemsIndexed(state.results, key = { _, hit -> hit.id }) { index, hit ->
                                ModRow(
                                    index = index + 1,
                                    hit = hit,
                                    downloading = state.downloadingId == hit.id,
                                    busy = state.downloadingId != null,
                                    onDownload = { vm.download(hit) }
                                )
                            }
                        }
                    }
                } else {
                    // Vista del Gestor Local
                    if (state.installedMods.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                PixelText(
                                    text = "No hay archivos en ${category?.folder ?: "mods"}",
                                    color = BedrockText,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                PixelText(
                                    text = "Descárgalos desde la pestaña EXPLORAR o colócalos en tu carpeta.",
                                    color = BedrockTextDim,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(bottom = 8.dp)
                        ) {
                            items(state.installedMods, key = { it.file.absolutePath }) { mod ->
                                InstalledModRow(
                                    mod = mod,
                                    onToggle = { vm.toggleMod(mod) },
                                    onDelete = { vm.deleteMod(mod) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InstalledModRow(
    mod: InstalledMod,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (mod.enabled) BedrockRow else BedrockVoid.copy(alpha = 0.5f))
            .border(
                1.dp,
                if (mod.enabled) BedrockGreen.copy(alpha = 0.6f) else BedrockBorderSoft.copy(alpha = 0.4f)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            PixelText(
                text = mod.name,
                color = if (mod.enabled) BedrockText else BedrockTextDim,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1
            )
            PixelText(
                text = "${mod.sizeKb} KB · ${if (mod.enabled) "Activo" else "Desactivado"}",
                color = if (mod.enabled) BedrockGreen else BedrockTextDim,
                style = MaterialTheme.typography.bodySmall
            )
        }

        McButton(
            text = if (mod.enabled) "ACTIVADO" else "DESACTIVADO",
            onClick = onToggle,
            tone = if (mod.enabled) McButtonTone.Success else McButtonTone.Ghost,
            height = 32.dp,
            modifier = Modifier.padding(end = 8.dp)
        )

        McButton(
            text = "",
            onClick = onDelete,
            icon = com.zyrovent.glauncher.ui.components.IconTrash,
            tone = McButtonTone.Danger,
            height = 32.dp,
            modifier = Modifier.size(36.dp)
        )
    }
}

@Composable
private fun ModRow(
    index: Int,
    hit: ModHit,
    downloading: Boolean,
    busy: Boolean,
    onDownload: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(BedrockVoid.copy(alpha = 0.5f))
            .border(1.dp, BedrockBorderSoft.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PixelText(
            text = index.toString(),
            color = BedrockTextDim,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(20.dp)
        )
        RemoteIcon(
            url = hit.iconUrl,
            size = 40.dp,
            fallback = IconBlock
        )
        Box(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            PixelText(
                text = hit.title,
                color = BedrockText,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1
            )
            PixelText(
                text = hit.description,
                color = BedrockTextDim,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2
            )
            PixelText(
                text = "${hit.author} · ${hit.downloadsLabel} descargas",
                color = Gold,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Box(Modifier.size(8.dp))
        // Solo el icono de descarga: no empuja el título ni la descripción.
        McButton(
            text = "",
            onClick = onDownload,
            enabled = !busy,
            icon = if (downloading) IconStop else IconDownload,
            tone = McButtonTone.Success,
            height = 34.dp,
            modifier = Modifier.width(44.dp)
        )
    }
}
