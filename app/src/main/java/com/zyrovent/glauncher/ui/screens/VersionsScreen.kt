package com.zyrovent.glauncher.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zyrovent.glauncher.R
import com.zyrovent.glauncher.UiState
import com.zyrovent.glauncher.data.VersionSummary
import com.zyrovent.glauncher.ui.components.BedrockTab
import com.zyrovent.glauncher.ui.components.BedrockWindow
import com.zyrovent.glauncher.ui.components.IconCheck
import com.zyrovent.glauncher.ui.components.IconList
import com.zyrovent.glauncher.ui.components.IconStar
import com.zyrovent.glauncher.ui.components.McButton
import com.zyrovent.glauncher.ui.components.McButtonTone
import com.zyrovent.glauncher.ui.components.McTag
import com.zyrovent.glauncher.ui.components.McTextField
import com.zyrovent.glauncher.ui.components.PixelIcon
import com.zyrovent.glauncher.ui.components.PixelText
import com.zyrovent.glauncher.ui.components.SectionLabel
import com.zyrovent.glauncher.ui.theme.BedrockBorder
import com.zyrovent.glauncher.ui.theme.BedrockBorderSoft
import com.zyrovent.glauncher.ui.theme.BedrockGreen
import com.zyrovent.glauncher.ui.theme.BedrockRow
import com.zyrovent.glauncher.ui.theme.BedrockText
import com.zyrovent.glauncher.ui.theme.BedrockTextDim
import com.zyrovent.glauncher.ui.theme.BedrockVoid
import com.zyrovent.glauncher.ui.theme.DangerRed
import com.zyrovent.glauncher.ui.theme.Gold

private enum class VersionFilter(val label: String) {
    ALL("Todas"), RELEASES("Releases"), SNAPSHOTS("Snapshots")
}

@Composable
fun VersionsScreen(
    state: UiState,
    onRefresh: () -> Unit,
    onSelect: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(VersionFilter.RELEASES) }

    val visible = remember(state.versions, query, filter) {
        state.versions.filter { version ->
            val matchesFilter = when (filter) {
                VersionFilter.ALL -> true
                VersionFilter.RELEASES -> !version.isSnapshot
                VersionFilter.SNAPSHOTS -> version.isSnapshot
            }
            val matchesQuery = query.isBlank() || version.id.contains(query.trim(), ignoreCase = true)
            matchesFilter && matchesQuery
        }
    }

    Row(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.fillMaxHeight().weight(0.34f)) {
            BedrockWindow(
                title = "Versiones",
                subtitle = if (state.latestRelease.isNotBlank()) {
                    "Última: ${state.latestRelease}"
                } else {
                    "Catálogo de Mojang"
                },
                icon = IconList,
                modifier = Modifier.fillMaxWidth().weight(1f),
                fillHeight = true,
                contentModifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                McTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Buscar (1.21.4)"
                )
                VersionFilter.entries.forEach { option ->
                    BedrockTab(
                        text = option.label,
                        selected = filter == option,
                        onClick = { filter = option }
                    )
                }
                McButton(
                    text = if (state.versionsLoading) "CARGANDO…" else "ACTUALIZAR",
                    onClick = onRefresh,
                    enabled = !state.versionsLoading,
                    tone = McButtonTone.Ghost,
                    modifier = Modifier.fillMaxWidth(),
                    height = 30.dp
                )
                SectionLabel("${visible.size} versiones visibles")
                PixelText(
                    text = "Activa: ${state.settings.selectedVersion}",
                    color = BedrockGreen,
                    style = MaterialTheme.typography.bodySmall
                )
                state.versionsError?.let { error ->
                    PixelText(
                        text = "Sin conexión: $error",
                        color = DangerRed,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2
                    )
                }
            }
        }

        Column(Modifier.fillMaxHeight().weight(0.66f)) {
            BedrockWindow(
                title = "Selecciona una versión",
                subtitle = "Toca una fila para aplicarla",
                icon = IconStar,
                modifier = Modifier.fillMaxWidth().weight(1f),
                trailing = { McTag("${visible.size}", BedrockTextDim) },
                fillHeight = true
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(visible, key = { it.id }) { version ->
                        VersionRow(
                            version = version,
                            selected = version.id == state.settings.selectedVersion,
                            installed = version.id in state.installedVersions,
                            onClick = { onSelect(version.id) }
                        )
                    }
                    if (visible.isEmpty() && !state.versionsLoading) {
                        item {
                            PixelText(
                                text = "No hay versiones que coincidan con el filtro.",
                                color = BedrockTextDim,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionRow(version: VersionSummary, selected: Boolean, installed: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(if (selected) BedrockRow else BedrockVoid.copy(alpha = 0.45f))
            .border(1.dp, if (selected) BedrockBorder else if (installed) BedrockGreen.copy(alpha = 0.4f) else BedrockBorderSoft.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.icon_vanilla),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Box(Modifier.size(8.dp))
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PixelText(
                        text = version.id,
                        color = BedrockText,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (installed && !selected) {
                        PixelIcon(art = IconCheck, size = 12.dp, tint = BedrockGreen)
                    }
                }
                PixelText(
                    text = "${version.displayType} · ${formatDate(version.releaseTime)}",
                    color = BedrockTextDim,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            when {
                selected -> McTag("ACTIVA", BedrockGreen)
                installed -> McTag("✓ LOCAL", BedrockGreen.copy(alpha = 0.8f))
                version.isSnapshot -> McTag("SNAP", Gold)
                else -> McTag("RELEASE", BedrockTextDim)
            }
        }
    }
}

fun formatDate(iso: String): String {
    if (iso.length < 10) return iso
    val parts = iso.substring(0, 10).split("-")
    return if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else iso.substring(0, 10)
}
