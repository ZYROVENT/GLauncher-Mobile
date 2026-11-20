package com.zyrovent.glauncher.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zyrovent.glauncher.ui.theme.BedrockBorder
import com.zyrovent.glauncher.ui.theme.BedrockBorderSoft
import com.zyrovent.glauncher.ui.theme.BedrockGreen
import com.zyrovent.glauncher.ui.theme.BedrockHighlight
import com.zyrovent.glauncher.ui.theme.BedrockRow
import com.zyrovent.glauncher.ui.theme.BedrockVoid

/**
 * Switch Animado tipo Bedrock / Cápsula según el dibujo de la libreta "Animaciones":
 * Desliza suavemente la perilla circular con borde, efecto de escala y cambio de color reactivo.
 */
@Composable
fun BedrockAnimatedSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 48.dp,
    height: Dp = 24.dp
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) (width - height) else 0.dp,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "switchThumbOffset"
    )

    val thumbScale by animateFloatAsState(
        targetValue = if (checked) 1.05f else 0.95f,
        animationSpec = tween(durationMillis = 180),
        label = "switchThumbScale"
    )

    val trackColor = if (checked) BedrockGreen.copy(alpha = 0.85f) else BedrockVoid.copy(alpha = 0.8f)
    val borderColor = if (checked) BedrockGreen else BedrockBorder

    Box(
        modifier = modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(height / 2))
            .background(trackColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(height / 2))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCheckedChange(!checked) }
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(height - 4.dp)
                .graphicsLayer {
                    scaleX = thumbScale
                    scaleY = thumbScale
                }
                .clip(CircleShape)
                .background(Color.White)
                .border(1.dp, BedrockBorderSoft, CircleShape)
        )
    }
}
