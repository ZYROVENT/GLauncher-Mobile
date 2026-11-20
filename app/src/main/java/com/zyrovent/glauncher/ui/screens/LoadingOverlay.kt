package com.zyrovent.glauncher.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zyrovent.glauncher.ui.components.BedrockProgress
import com.zyrovent.glauncher.ui.components.BedrockWindow
import com.zyrovent.glauncher.ui.components.PixelText
import com.zyrovent.glauncher.ui.theme.BedrockTextDim
import com.zyrovent.glauncher.ui.theme.BedrockVoid

@Composable
fun LoadingOverlay(
    visible: Boolean,
    title: String,
    detail: String,
    progress: Float,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(120)),
        modifier = modifier
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(BedrockVoid.copy(alpha = 0.88f)),
            contentAlignment = Alignment.Center
        ) {
            BedrockWindow(
                title = title,
                subtitle = "${(progress * 100).toInt()}%",
                modifier = Modifier.widthIn(max = 380.dp)
            ) {
                PixelText(
                    text = detail,
                    color = BedrockTextDim,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3
                )
                BedrockProgress(progress, Modifier.fillMaxWidth())
            }
        }
    }
}
