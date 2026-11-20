package com.zyrovent.glauncher.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zyrovent.glauncher.Screen
import com.zyrovent.glauncher.UiState
import com.zyrovent.glauncher.data.LauncherEvent
import com.zyrovent.glauncher.ui.components.IconBell
import com.zyrovent.glauncher.ui.components.IconBlock
import com.zyrovent.glauncher.ui.components.IconChat
import com.zyrovent.glauncher.ui.components.IconClose
import com.zyrovent.glauncher.ui.components.IconDownload
import com.zyrovent.glauncher.ui.components.IconGear
import com.zyrovent.glauncher.ui.components.IconHead
import com.zyrovent.glauncher.ui.components.IconHome
import com.zyrovent.glauncher.ui.components.IconList
import com.zyrovent.glauncher.ui.components.IconNote
import com.zyrovent.glauncher.ui.components.IconPlay
import com.zyrovent.glauncher.ui.components.McButton
import com.zyrovent.glauncher.ui.components.McButtonTone
import com.zyrovent.glauncher.ui.components.PixelIcon
import com.zyrovent.glauncher.ui.components.PixelText
import com.zyrovent.glauncher.ui.theme.BedrockBar
import com.zyrovent.glauncher.ui.theme.BedrockBorder
import com.zyrovent.glauncher.ui.theme.BedrockBorderSoft
import com.zyrovent.glauncher.ui.theme.BedrockGold
import com.zyrovent.glauncher.ui.theme.BedrockGreen
import com.zyrovent.glauncher.ui.theme.BedrockPanel
import com.zyrovent.glauncher.ui.theme.BedrockRow
import com.zyrovent.glauncher.ui.theme.BedrockText
import com.zyrovent.glauncher.ui.theme.BedrockTextDim
import com.zyrovent.glauncher.ui.theme.BedrockVoid
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Menú superior (Upper Menu): se desliza desde la izquierda con la marca, la
 * navegación, el aviso de novedades y el pie con versión + jugar.
 */
@Composable
fun HomeUpperMenu(
    visible: Boolean,
    state: UiState,
    launcherVersion: String,
    onClose: () -> Unit,
    onNavigate: (Screen) -> Unit,
    onUpdate: () -> Unit,
    onNotifications: () -> Unit,
    onProfile: () -> Unit,
    onPlay: () -> Unit
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        // Velo: un toque fuera del panel lo cierra.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BedrockVoid.copy(alpha = 0.6f))
                .clickable { onClose() }
        )
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally { -it } + fadeIn(),
        exit = slideOutHorizontally { -it } + fadeOut()
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(268.dp)
                .background(BedrockPanel)
                .border(1.dp, BedrockBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BedrockBar)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    PixelText(
                        text = "GLAUNCHER",
                        color = BedrockGold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    PixelText(
                        text = "A Minecraft Launcher",
                        color = BedrockTextDim,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Box(
                    modifier = Modifier.size(26.dp).clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    PixelIcon(art = IconClose, size = 12.dp, tint = BedrockTextDim)
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 6.dp)
            ) {
                MenuEntry("Inicio", IconHome, resaltado = state.screen == Screen.HOME) {
                    onNavigate(Screen.HOME)
                }
                MenuEntry("Versiones", IconBlock, resaltado = state.screen == Screen.VERSIONS) {
                    onNavigate(Screen.VERSIONS)
                }
                MenuEntry("Cuentas", IconHead, resaltado = state.screen == Screen.ACCOUNTS) {
                    onNavigate(Screen.ACCOUNTS)
                }
                MenuEntry("GMusic", IconNote, resaltado = state.screen == Screen.MUSIC) {
                    onNavigate(Screen.MUSIC)
                }
                MenuEntry("Mods", IconList, resaltado = state.screen == Screen.MODS) {
                    onNavigate(Screen.MODS)
                }
                MenuEntry("Ajustes", IconGear, resaltado = state.screen == Screen.SETTINGS) {
                    onNavigate(Screen.SETTINGS)
                }
                MenuEntry("Registro", IconChat, resaltado = false) {
                    onNavigate(Screen.SETTINGS)
                }

                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .height(1.dp)
                        .background(BedrockBorderSoft)
                )
                Spacer(Modifier.height(4.dp))

                val sinLeer = state.events.count { !it.read }
                MenuEntry(
                    text = if (sinLeer > 0) "Notificaciones ($sinLeer)" else "Notificaciones",
                    art = IconBell,
                    resaltado = false,
                    onClick = onNotifications
                )
                MenuEntry("Mi cuenta", IconHead, resaltado = false, onClick = onProfile)
                MenuEntry("Actualizaciones", IconDownload, resaltado = false, onClick = onUpdate)
            }

            // Pie: versión seleccionada + jugar.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BedrockVoid.copy(alpha = 0.45f))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BedrockRow)
                        .border(1.dp, BedrockBorder)
                        .clickable { onNavigate(Screen.VERSIONS) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PixelIcon(art = IconBlock, size = 13.dp, tint = BedrockGreen)
                    Box(Modifier.width(6.dp))
                    PixelText(
                        text = "Minecraft ${state.settings.selectedVersion.ifBlank { "1.18" }}",
                        color = BedrockText,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f)
                    )
                    PixelText(
                        text = "▼",
                        color = BedrockTextDim,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                McButton(
                    text = if (state.gameRunning) "VOLVER AL JUEGO" else if (state.launching) "INICIANDO…" else "JUGAR",
                    onClick = onPlay,
                    enabled = !state.launching,
                    tone = McButtonTone.Success,
                    icon = IconPlay,
                    modifier = Modifier.fillMaxWidth(),
                    height = 40.dp
                )
                PixelText(
                    text = "GLauncher v$launcherVersion",
                    color = BedrockTextDim,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun MenuEntry(
    text: String,
    art: com.zyrovent.glauncher.ui.components.PixelArt,
    resaltado: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (resaltado) BedrockRow else BedrockPanel)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PixelIcon(
            art = art,
            size = 14.dp,
            tint = if (resaltado) BedrockGold else BedrockTextDim
        )
        Box(Modifier.width(9.dp))
        PixelText(
            text = text,
            color = if (resaltado) BedrockText else BedrockTextDim,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** Modal de actualización: versión instalada y búsqueda local. */
@Composable
fun UpdateModal(
    visible: Boolean,
    state: UiState,
    launcherVersion: String,
    onClose: () -> Unit
) {
    if (!visible) return
    val release = state.remoteRelease
    var estado by remember(release) {
        mutableStateOf(
            when {
                release == null -> "Toca BUSCAR para comprobar el canal."
                release.version == launcherVersion -> "Estás en la última versión publicada (v${release.version})."
                else -> "Nueva versión v${release.version} disponible.\n${release.notes}"
            }
        )
    }
    var buscando by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BedrockVoid.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth(0.9f)
                .background(BedrockPanel)
                .border(2.dp, BedrockBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BedrockBar)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PixelIcon(art = IconDownload, size = 15.dp, tint = BedrockGold)
                Box(Modifier.width(8.dp))
                PixelText(
                    text = "ACTUALIZACIÓN",
                    color = BedrockText,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier.size(26.dp).clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    PixelIcon(art = IconClose, size = 12.dp, tint = BedrockTextDim)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PixelIcon(art = IconBlock, size = 34.dp, tint = BedrockGreen)
                    Box(Modifier.width(10.dp))
                    Column {
                        PixelText(
                            text = "GLauncher v$launcherVersion",
                            color = BedrockText,
                            style = MaterialTheme.typography.titleSmall
                        )
                        PixelText(
                            text = "Minecraft ${state.settings.selectedVersion}",
                            color = BedrockTextDim,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BedrockVoid)
                        .border(1.dp, BedrockBorderSoft)
                        .padding(9.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        PixelText(
                            text = "Descripción",
                            color = BedrockTextDim,
                            style = MaterialTheme.typography.labelMedium
                        )
                        PixelText(
                            text = estado,
                            color = BedrockText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                McButton(
                    text = if (buscando) "BUSCANDO…" else "BUSCAR ACTUALIZACIÓN",
                    onClick = {
                        if (buscando) return@McButton
                        buscando = true
                        estado = "Consultando el canal de actualizaciones…"
                        state.let { }
                        scope.launch {
                            delay(900)
                            buscando = false
                            val publicada = com.zyrovent.glauncher.data.SupabaseApi.latestRelease()
                            estado = when {
                                publicada == null ->
                                    "No hay canal publicado; sigues en v$launcherVersion."
                                publicada.version == launcherVersion ->
                                    "Estás en la última versión publicada (v${publicada.version})."
                                else -> "Nueva versión v${publicada.version} disponible.\n${publicada.notes}"
                            }
                        }
                    },
                    enabled = !buscando,
                    tone = McButtonTone.Primary,
                    modifier = Modifier.fillMaxWidth(),
                    height = 40.dp
                )
            }
        }
    }
}

/** Notificaciones: historial de avisos del launcher. */
@Composable
fun NotificationsModal(
    visible: Boolean,
    events: List<LauncherEvent>,
    remote: List<com.zyrovent.glauncher.data.RemoteNotification> = emptyList(),
    onClose: () -> Unit,
    onClear: () -> Unit
) {
    if (!visible) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BedrockVoid.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.92f)
                .background(BedrockPanel)
                .border(2.dp, BedrockBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BedrockBar)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PixelIcon(art = IconBell, size = 15.dp, tint = BedrockGold)
                Box(Modifier.width(8.dp))
                PixelText(
                    text = "NOTIFICACIONES (${events.size + remote.size})",
                    color = BedrockText,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier.size(26.dp).clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    PixelIcon(art = IconClose, size = 12.dp, tint = BedrockTextDim)
                }
            }

            if (events.isEmpty() && remote.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    PixelText(
                        text = "Sin notificaciones por ahora.",
                        color = BedrockTextDim,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    items(remote) { notice ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BedrockRow)
                                .border(1.dp, BedrockBorderSoft)
                                .padding(9.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PixelText(
                                    text = notice.title,
                                    color = BedrockText,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                PixelText(
                                    text = when (notice.kind) {
                                        "update" -> "VERSIÓN"
                                        "event" -> "EVENTO"
                                        "warning" -> "AVISO"
                                        else -> "INFO"
                                    },
                                    color = BedrockGold,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            if (notice.body.isNotBlank()) {
                                PixelText(
                                    text = notice.body,
                                    color = BedrockTextDim,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    items(events) { event ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BedrockRow)
                                .border(1.dp, BedrockBorderSoft)
                                .padding(9.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PixelText(
                                    text = event.title,
                                    color = BedrockText,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                PixelText(
                                    text = hace(event.time),
                                    color = BedrockTextDim,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            PixelText(
                                text = event.detail,
                                color = BedrockTextDim,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BedrockVoid.copy(alpha = 0.45f))
                    .padding(9.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                McButton(
                    text = "LIMPIAR",
                    onClick = onClear,
                    tone = McButtonTone.Ghost,
                    modifier = Modifier.weight(1f),
                    height = 36.dp
                )
                McButton(
                    text = "CERRAR",
                    onClick = onClose,
                    tone = McButtonTone.Primary,
                    modifier = Modifier.weight(1f),
                    height = 36.dp
                )
            }
        }
    }
}

/** "ahora", "hace 5 min", "hace 2 h", "hace 3 d". */
private fun hace(time: Long): String {
    val diff = (System.currentTimeMillis() - time) / 1000
    return when {
        diff < 60 -> "ahora"
        diff < 3600 -> "hace ${diff / 60} min"
        diff < 86_400 -> "hace ${diff / 3600} h"
        else -> "hace ${diff / 86_400} d"
    }
}
