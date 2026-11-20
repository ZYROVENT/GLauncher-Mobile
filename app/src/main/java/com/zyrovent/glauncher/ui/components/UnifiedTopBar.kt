package com.zyrovent.glauncher.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.zyrovent.glauncher.R
import com.zyrovent.glauncher.Screen
import com.zyrovent.glauncher.UiState
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
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Icono pixel-art de rayo cargando para la batería según el boceto
 */
val IconBatteryBolt = PixelArt(
    listOf(
        "........##......",
        ".......####.....",
        "......######....",
        ".....######.....",
        "....######......",
        "...########.....",
        "..##########....",
        "################",
        "################",
        "....##########..",
        ".....########...",
        "......######....",
        ".......####.....",
        "........##......",
        "........#.......",
        "................"
    )
)

/**
 * Indicador de batería pixelado con porcentaje y animación de carga
 */
@Composable
fun BatteryStatusView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var batteryLevel by remember { mutableIntStateOf(100) }
    var isCharging by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                intent?.let {
                    val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    if (level >= 0 && scale > 0) {
                        batteryLevel = (level * 100) / scale
                    }
                    val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                 status == BatteryManager.BATTERY_STATUS_FULL
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val sticky = context.registerReceiver(receiver, filter)
        sticky?.let {
            val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) {
                batteryLevel = (level * 100) / scale
            }
            val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                         status == BatteryManager.BATTERY_STATUS_FULL
        }

        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "chargingAnim")
    val boltAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "boltPulse"
    )

    Row(
        modifier = modifier
            .background(BedrockVoid.copy(alpha = 0.6f))
            .border(1.dp, BedrockBorderSoft)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (isCharging) {
            PixelIcon(
                art = IconBatteryBolt,
                size = 12.dp,
                tint = BedrockGreen.copy(alpha = boltAlpha)
            )
        }
        // Caja de batería estilo SVG / Bedrock pixelada
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(22.dp)
                    .height(11.dp)
                    .background(BedrockVoid)
                    .border(1.5.dp, BedrockBorder)
                    .padding(1.5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = (batteryLevel / 100f).coerceIn(0.08f, 1f))
                        .background(
                            when {
                                batteryLevel <= 15 -> Color(0xFFEF5350)
                                batteryLevel <= 30 -> BedrockGold
                                else -> BedrockGreen
                            }
                        )
                )
            }
            // Borne terminal de la batería
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height(5.dp)
                    .background(BedrockBorder)
            )
        }
        PixelText(
            text = "$batteryLevel%",
            color = BedrockTextDim,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

/**
 * Reloj digital estilo Minecraft con fuente pixelada
 */
@Composable
fun ClockView(modifier: Modifier = Modifier) {
    var timeString by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            timeString = sdf.format(Date())
            delay(1000)
        }
    }

    PixelText(
        text = timeString,
        color = BedrockText,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier
    )
}

/**
 * Barra superior unificada de GLauncher Mobile según el Boceto de la libreta:
 * [Title.png / Logo] | [Espacio] | [Iconos de estado / Reloj / Batería] | [Campana] | [Avatar / Cuenta]
 */
@Composable
fun UnifiedTopBar(
    state: UiState,
    launcherVersion: String,
    onMenu: () -> Unit,
    onNotifications: () -> Unit,
    onAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    val account = state.selectedAccount
    val pendientes = state.events.count { !it.read } + state.remoteNotifications.size
    val gss = com.zyrovent.glauncher.gss.LocalGssTheme.current
    val navStyle = gss.navbar

    val barBg = navStyle.fondo ?: BedrockPanel.copy(alpha = 0.85f)
    val barBorder = navStyle.borde ?: BedrockBorderSoft

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(barBg)
            .border(1.dp, barBorder)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Título del juego / Logo a la izquierda
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.clickable { onMenu() }
        ) {
            Image(
                painter = painterResource(R.drawable.title_glaucher),
                contentDescription = "GLauncher",
                modifier = Modifier.height(20.dp)
            )
            PixelText(
                text = "v$launcherVersion",
                color = BedrockGold,
                style = MaterialTheme.typography.labelSmall
            )
        }

        Spacer(Modifier.weight(1f))

        // Sección central / derecha: Reloj Minecraft + Batería con animación SVG/Pixel
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ClockView()
            BatteryStatusView()
        }

        Spacer(Modifier.width(8.dp))

        // Campana de notificaciones con punto rojo
        Box(
            modifier = Modifier
                .size(30.dp)
                .clickable { onNotifications() },
            contentAlignment = Alignment.Center
        ) {
            PixelIcon(art = IconBell, size = 15.dp, tint = BedrockText)
            if (pendientes > 0) {
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

        Spacer(Modifier.width(4.dp))

        // Perfil / MiCuenta Info Card según el boceto
        Row(
            modifier = Modifier
                .height(28.dp)
                .background(BedrockVoid.copy(alpha = 0.6f))
                .border(1.dp, BedrockBorderSoft)
                .clickable { onAccount() }
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PlayerAvatar(size = 18.dp)
            PixelText(
                text = account?.username ?: "MiCuenta",
                color = if (account != null) BedrockText else BedrockTextDim,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
