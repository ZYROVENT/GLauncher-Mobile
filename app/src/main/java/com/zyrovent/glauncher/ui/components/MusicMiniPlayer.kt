package com.zyrovent.glauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zyrovent.glauncher.data.MusicTrack
import com.zyrovent.glauncher.ui.theme.BedrockGreen
import com.zyrovent.glauncher.ui.theme.BedrockText
import com.zyrovent.glauncher.ui.theme.BedrockTextDim
import com.zyrovent.glauncher.ui.theme.BedrockVoid
import com.zyrovent.glauncher.ui.theme.Gold

/**
 * Barra "ahora suena" al estilo Spotify: sigue visible en cualquier pestaña
 * mientras GMusic tenga audio activo, y también durante la partida. Toca la
 * pista para abrir el reproductor completo.
 */
@Composable
fun MusicMiniPlayer(
    track: MusicTrack,
    paused: Boolean,
    buffering: Boolean,
    onOpen: () -> Unit,
    onTogglePause: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .widthIn(max = 430.dp)
            .background(BedrockVoid.copy(alpha = 0.92f))
            .border(1.dp, BedrockGreen.copy(alpha = 0.55f))
            .clickable(onClick = onOpen)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RemoteIcon(url = track.thumbnail, size = 32.dp, fallback = IconNote)
        Column(Modifier.weight(1f)) {
            PixelText(
                text = track.title,
                color = BedrockText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
            PixelText(
                text = if (buffering) "cargando…" else track.uploader,
                color = if (buffering) Gold else BedrockTextDim,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
        McTag(
            if (buffering) "CARGANDO" else if (paused) "EN PAUSA" else "SONANDO",
            if (buffering) Gold else if (paused) BedrockTextDim else BedrockGreen
        )
        McButton(
            text = if (paused) "PLAY" else "II",
            onClick = onTogglePause,
            icon = if (paused) IconPlay else IconPause,
            tone = McButtonTone.Success,
            height = 28.dp
        )
        McButton(
            text = "X",
            onClick = onStop,
            icon = IconClose,
            tone = McButtonTone.Danger,
            height = 28.dp
        )
    }
}
