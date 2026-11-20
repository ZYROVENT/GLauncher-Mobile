package com.zyrovent.glauncher.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.zyrovent.glauncher.R
import com.zyrovent.glauncher.Screen
import com.zyrovent.glauncher.UiState
import com.zyrovent.glauncher.ui.components.BedrockProgress
import com.zyrovent.glauncher.ui.components.IconBell
import com.zyrovent.glauncher.ui.components.IconBlock
import com.zyrovent.glauncher.ui.components.IconMenu
import com.zyrovent.glauncher.ui.components.IconPlay
import com.zyrovent.glauncher.ui.components.IconStop
import com.zyrovent.glauncher.ui.components.McButton
import com.zyrovent.glauncher.ui.components.McButtonTone
import com.zyrovent.glauncher.ui.components.PixelIcon
import com.zyrovent.glauncher.ui.components.PixelText
import com.zyrovent.glauncher.ui.components.PlayerAvatar
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
import com.zyrovent.glauncher.ui.theme.Gold

/**
 * Inicio rediseñado: barra superior con menú, campana y cuenta; banner del
 * launcher con el selector de versión y jugar a la izquierda, y el panel de
 * novedades a la derecha.
 */
@Composable
fun HomeScreen(
    state: UiState,
    launcherVersion: String,
    onPlay: () -> Unit,
    onResumeGame: () -> Unit,
    onStop: () -> Unit,
    onPickVersion: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenSettings: () -> Unit,
    onNavigate: (Screen) -> Unit,
    onMarkEventsRead: () -> Unit,
    onClearEvents: () -> Unit
) {
    val account = state.selectedAccount
    val pendientes = state.events.count { !it.read } + state.remoteNotifications.size

    var showMenu by remember { mutableStateOf(false) }
    var showNotifications by remember { mutableStateOf(false) }
    var showUpdate by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }

    // Abrir la campana marca todo como leído y apaga el punto rojo.
    LaunchedEffect(showNotifications) {
        if (showNotifications) onMarkEventsRead()
    }

    Box(
        modifier = Modifier.fillMaxSize().padding(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 8.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ---- Columna del launcher: banner, versión y jugar ----
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Image(
                        painter = painterResource(R.drawable.title_glaucher),
                        contentDescription = "GLauncher",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2048f / 342f)
                            .padding(top = 2.dp)
                    )
                    PixelText(
                        text = "A Minecraft Launcher",
                        color = BedrockTextDim,
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(Modifier.weight(1f))

                    // Selector de versión.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BedrockRow.copy(alpha = 0.8f))
                            .border(1.dp, BedrockBorder)
                            .clickable { onPickVersion() }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PixelIcon(art = IconBlock, size = 16.dp, tint = BedrockGreen)
                        Box(Modifier.width(8.dp))
                        PixelText(
                            text = "Minecraft ${state.settings.selectedVersion.ifBlank { "1.18" }}",
                            color = BedrockText,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        PixelText(
                            text = "▼",
                            color = BedrockTextDim,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    if (state.gameRunning) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            McButton(
                                text = "VOLVER AL JUEGO",
                                onClick = onResumeGame,
                                tone = McButtonTone.Primary,
                                icon = IconPlay,
                                modifier = Modifier.weight(1f),
                                height = 46.dp
                            )
                            McButton(
                                text = "CERRAR",
                                onClick = onStop,
                                tone = McButtonTone.Danger,
                                icon = IconStop,
                                modifier = Modifier.width(112.dp),
                                height = 46.dp
                            )
                        }
                    } else {
                        McButton(
                            text = if (state.launching) "INICIANDO…" else "JUGAR",
                            onClick = onPlay,
                            enabled = !state.launching,
                            tone = McButtonTone.Success,
                            icon = IconPlay,
                            modifier = Modifier.fillMaxWidth(),
                            height = 46.dp
                        )
                    }
                }

                // ---- Panel de novedades ----
                WhatsNewPanel(
                    state = state,
                    launcherVersion = launcherVersion,
                    modifier = Modifier.width(238.dp).fillMaxHeight()
                )
            }

            Spacer(Modifier.height(6.dp))

            // Franja de estado: progreso al arrancar, versión el resto del tiempo.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BedrockVoid.copy(alpha = 0.45f))
                    .border(1.dp, BedrockBorderSoft.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (state.launching) {
                    BedrockProgress(state.progress, Modifier.fillMaxWidth())
                } else {
                    PixelText(
                        text = state.stage.ifBlank { "Listo · ${state.settings.selectedVersion}" },
                        color = BedrockTextDim,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        HomeUpperMenu(
            visible = showMenu,
            state = state,
            launcherVersion = launcherVersion,
            onClose = { showMenu = false },
            onNavigate = { screen ->
                showMenu = false
                onNavigate(screen)
            },
            onUpdate = {
                showMenu = false
                showUpdate = true
            },
            onProfile = {
                showMenu = false
                showProfile = true
            },
            onNotifications = {
                showMenu = false
                showNotifications = true
            },
            onPlay = {
                showMenu = false
                onPlay()
            }
        )

        UpdateModal(
            visible = showUpdate,
            state = state,
            launcherVersion = launcherVersion,
            onClose = { showUpdate = false }
        )

        NotificationsModal(
            visible = showNotifications,
            events = state.events,
            remote = state.remoteNotifications,
            onClose = { showNotifications = false },
            onClear = onClearEvents
        )

        ProfileModal(
            visible = showProfile,
            account = account,
            selectedVersion = state.settings.selectedVersion,
            onClose = { showProfile = false },
            onOpenAccounts = { onOpenAccounts() }
        )
    }
}

/** Barra superior: menú a la izquierda, campana y cuenta a la derecha. */
@Composable
private fun HomeTopBar(
    title: String,
    username: String,
    hasAccount: Boolean,
    unread: Int,
    onMenu: () -> Unit,
    onNotifications: () -> Unit,
    onAccount: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(BedrockPanel.copy(alpha = 0.72f))
            .border(1.dp, BedrockBorderSoft)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clickable { onMenu() },
            contentAlignment = Alignment.Center
        ) {
            PixelIcon(art = IconMenu, size = 16.dp, tint = BedrockText)
        }
        Box(Modifier.width(8.dp))
        PixelText(
            text = title,
            color = BedrockGold,
            style = MaterialTheme.typography.titleSmall
        )

        Spacer(Modifier.weight(1f))

        // Campana con punto rojo si hay avisos sin leer.
        Box(
            modifier = Modifier.size(30.dp).clickable { onNotifications() },
            contentAlignment = Alignment.Center
        ) {
            PixelIcon(art = IconBell, size = 15.dp, tint = BedrockText)
            if (unread > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 4.dp, end = 4.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Gold)
                )
            }
        }

        Box(Modifier.width(6.dp))

        Row(
            modifier = Modifier
                .height(28.dp)
                .background(BedrockVoid.copy(alpha = 0.6f))
                .border(1.dp, BedrockBorderSoft)
                .clickable { onAccount() }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerAvatar(size = 18.dp)
            Box(Modifier.width(6.dp))
            PixelText(
                text = username,
                color = if (hasAccount) BedrockText else Gold,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

/** Panel "what's new?": novedades reales del launcher y últimos avisos. */
@Composable
private fun WhatsNewPanel(
    state: UiState,
    launcherVersion: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(BedrockPanel.copy(alpha = 0.9f))
            .border(1.dp, BedrockBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BedrockBar)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PixelText(
                text = "WHAT'S NEW?",
                color = BedrockText,
                style = MaterialTheme.typography.labelLarge
            )
            PixelText(
                text = "v$launcherVersion",
                color = BedrockGold,
                style = MaterialTheme.typography.labelSmall
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(9.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            item {
                NewsItem(
                    title = "GLauncher v$launcherVersion",
                    detail = "Inicio nuevo: menú superior, campana de avisos y panel de novedades.",
                    accent = BedrockGreen
                )
            }
            item {
                NewsItem(
                    title = "Minecraft ${state.settings.selectedVersion}",
                    detail = if (state.latestRelease.isNotBlank()) {
                        "Última release de Mojang: ${state.latestRelease}"
                    } else {
                        "Perfil listo para arrancar."
                    },
                    accent = BedrockGold
                )
            }
            items(state.events.take(4)) { event ->
                NewsItem(
                    title = event.title,
                    detail = event.detail,
                    accent = BedrockTextDim
                )
            }
            if (state.events.isEmpty()) {
                item {
                    PixelText(
                        text = "Sin avisos por ahora.",
                        color = BedrockTextDim,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BedrockVoid.copy(alpha = 0.5f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PixelText(
                text = "GLauncher",
                color = BedrockTextDim,
                style = MaterialTheme.typography.labelSmall
            )
            PixelText(
                text = "v$launcherVersion",
                color = BedrockTextDim,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun NewsItem(title: String, detail: String, accent: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 4.dp)
                .size(5.dp)
                .background(accent)
        )
        Box(Modifier.width(7.dp))
        Column(Modifier.weight(1f)) {
            PixelText(
                text = title,
                color = BedrockText,
                style = MaterialTheme.typography.labelMedium
            )
            PixelText(
                text = detail,
                color = BedrockTextDim,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun ProgressStrip(progress: Float, modifier: Modifier = Modifier) {
    BedrockProgress(progress, modifier.fillMaxWidth())
}
