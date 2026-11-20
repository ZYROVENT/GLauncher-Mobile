package com.zyrovent.glauncher.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import com.zyrovent.glauncher.gss.GssEngine
import com.zyrovent.glauncher.gss.LocalGssTheme

@Composable
fun GLauncherTheme(content: @Composable () -> Unit) {
    val gss = GssEngine.currentTheme

    val cardBg = gss.card.fondo ?: BedrockPanel
    val barBg = gss.navbar.fondo ?: BedrockBar
    val btnBg = gss.button.fondo ?: BedrockGreen
    val primaryText = gss.button.letra ?: BedrockText
    val borderColor = gss.card.borde ?: BedrockBorder

    val dynamicColors = darkColorScheme(
        primary = btnBg,
        onPrimary = BedrockVoid,
        secondary = gss.navbarBtnActive.letra ?: BedrockGreen,
        onSecondary = BedrockVoid,
        background = BedrockVoid,
        onBackground = primaryText,
        surface = cardBg,
        onSurface = primaryText,
        surfaceVariant = barBg,
        onSurfaceVariant = BedrockTextDim,
        outline = borderColor,
        error = BedrockRed
    )

    val radius = gss.button.redondeado ?: 2.dp
    val dynamicShapes = Shapes(
        extraSmall = RoundedCornerShape((radius / 2).coerceAtLeast(1.dp)),
        small = RoundedCornerShape(radius),
        medium = RoundedCornerShape(radius),
        large = RoundedCornerShape(radius * 1.5f),
        extraLarge = RoundedCornerShape(radius * 2f)
    )

    CompositionLocalProvider(LocalGssTheme provides gss) {
        MaterialTheme(
            colorScheme = dynamicColors,
            typography = GLauncherTypography,
            shapes = dynamicShapes,
            content = content
        )
    }
}
