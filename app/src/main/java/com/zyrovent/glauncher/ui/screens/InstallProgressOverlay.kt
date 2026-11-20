package com.zyrovent.glauncher.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zyrovent.glauncher.ui.components.BedrockProgress
import com.zyrovent.glauncher.ui.components.BedrockWindow
import com.zyrovent.glauncher.ui.components.IconClose
import com.zyrovent.glauncher.ui.components.McButton
import com.zyrovent.glauncher.ui.components.McButtonTone
import com.zyrovent.glauncher.ui.components.PixelText
import com.zyrovent.glauncher.ui.theme.BedrockGreen
import com.zyrovent.glauncher.ui.theme.BedrockTextDim
import com.zyrovent.glauncher.ui.theme.BedrockVoid

/** Ventana de Bedrock que tapa la preparación de la partida y permite cancelar. */
@Composable
fun InstallProgressOverlay(
    stage: String,
    progress: Float,
    onCancel: () -> Unit
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(120)),
        label = "installOverlay"
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(BedrockVoid.copy(alpha = 0.88f)),
            contentAlignment = Alignment.Center
        ) {
            BedrockWindow(
                title = "Preparando la partida",
                subtitle = "${(progress * 100).toInt()}% completado",
                modifier = Modifier.widthIn(max = 420.dp)
            ) {
                PixelText(
                    text = stage.ifBlank { "Trabajando…" },
                    color = BedrockTextDim,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3
                )
                BedrockProgress(progress, Modifier.fillMaxWidth())
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PixelText(
                        text = "GLauncher",
                        color = BedrockGreen,
                        style = MaterialTheme.typography.labelSmall
                    )
                    McButton(
                        text = "CANCELAR",
                        onClick = onCancel,
                        icon = IconClose,
                        tone = McButtonTone.Danger,
                        height = 30.dp,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }
        }
    }
}
