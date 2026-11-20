package com.zyrovent.glauncher.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zyrovent.glauncher.Screen
import com.zyrovent.glauncher.ui.theme.BedrockBar
import com.zyrovent.glauncher.ui.theme.BedrockBorder
import com.zyrovent.glauncher.ui.theme.BedrockBorderSoft
import com.zyrovent.glauncher.ui.theme.BedrockGreen
import com.zyrovent.glauncher.ui.theme.BedrockHighlight
import com.zyrovent.glauncher.ui.theme.BedrockTextDim
import com.zyrovent.glauncher.ui.theme.BedrockVoid

private data class NavDestination(val screen: Screen, val icon: PixelArt)

private val destinations = listOf(
    NavDestination(Screen.HOME, IconHome),
    NavDestination(Screen.VERSIONS, IconList),
    NavDestination(Screen.ACCOUNTS, IconHead),
    NavDestination(Screen.MUSIC, IconNote),
    NavDestination(Screen.SHOP, IconCart),
    NavDestination(Screen.MODS, IconBlock),
    NavDestination(Screen.SETTINGS, IconGear)
)

private const val ITEM_SIZE = 40
private const val ITEM_GAP = 2

/**
 * Barra lateral flotante con la estética de Bedrock: marco claro, ranuras
 * cuadradas, un realce verde en la opción activa y iconos pixel-art dibujados a mano.
 */
@Composable
fun FloatingNavRail(
    current: Screen,
    onSelect: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val gss = com.zyrovent.glauncher.gss.LocalGssTheme.current
    val navStyle = gss.navbar
    val navBtnStyle = gss.navbarBtn
    val navActiveStyle = gss.navbarBtnActive

    val index = destinations.indexOfFirst { it.screen == current }.coerceAtLeast(0)
    val offset by animateDpAsState(
        targetValue = (index * (ITEM_SIZE + ITEM_GAP)).dp,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "navIndicator"
    )

    val railBg = navStyle.fondo ?: BedrockBar.copy(alpha = 0.95f)
    val railBorder = navStyle.borde ?: BedrockBorder
    val activeBorder = navActiveStyle.borde ?: BedrockGreen
    val activeBg = navActiveStyle.fondo ?: BedrockVoid

    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f))
            .border(2.dp, railBorder)
            .padding(2.dp)
            .background(railBg)
            .padding(2.dp)
    ) {
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset(y = offset)
                .size(ITEM_SIZE.dp)
                .background(activeBg)
                .border(1.dp, activeBorder)
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(ITEM_GAP.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            destinations.forEach { destination ->
                val selected = destination.screen == current
                Box(
                    modifier = Modifier
                        .size(ITEM_SIZE.dp)
                        .background(if (selected) BedrockVoid.copy(alpha = 0.35f) else Color.Transparent)
                        .border(
                            1.dp,
                            if (selected) railBorder else BedrockBorderSoft.copy(alpha = 0.35f)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(destination.screen) },
                    contentAlignment = Alignment.Center
                ) {
                    PixelIcon(
                        art = destination.icon,
                        size = 20.dp,
                        tint = if (selected) (navActiveStyle.letra ?: BedrockHighlight) else (navBtnStyle.letra ?: BedrockTextDim),
                        accent = BedrockGreen
                    )
                }
            }
        }
    }
}
