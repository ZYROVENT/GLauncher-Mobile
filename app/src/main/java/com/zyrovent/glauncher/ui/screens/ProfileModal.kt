package com.zyrovent.glauncher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zyrovent.glauncher.data.Account
import com.zyrovent.glauncher.data.AccountType
import com.zyrovent.glauncher.data.StreakStore
import com.zyrovent.glauncher.ui.components.IconBack
import com.zyrovent.glauncher.ui.components.IconClose
import com.zyrovent.glauncher.ui.components.IconHead
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

/**
 * Ficha MiCuenta-Info del boceto: avatar, sesión, GCoins, nivel, descripción y
 * pestañas Info / Logros. Todo lo que muestra sale de datos reales del
 * dispositivo (perfil activo y racha); cuando entre Supabase serán
 * `get_my_profile()` y `get_my_streak`.
 */
@Composable
fun ProfileModal(
    visible: Boolean,
    account: Account?,
    selectedVersion: String,
    onClose: () -> Unit,
    onOpenAccounts: () -> Unit
) {
    if (!visible) return

    val context = LocalContext.current
    val streakStore = remember { StreakStore(context.applicationContext) }
    val streak by streakStore.state.collectAsState()

    var tab by remember { mutableStateOf(0) } // 0 = info, 1 = logros

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BedrockVoid.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth(0.94f)
                .background(BedrockPanel)
                .border(2.dp, BedrockBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BedrockBar)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PixelIcon(art = IconHead, size = 15.dp, tint = BedrockGold)
                Box(Modifier.width(8.dp))
                PixelText(
                    text = "MI CUENTA",
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Columna del avatar, como en el dibujo.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(BedrockVoid)
                            .border(2.dp, BedrockBorder),
                        contentAlignment = Alignment.Center
                    ) {
                        PlayerAvatar(size = 56.dp)
                    }
                    PixelText(
                        text = account?.username ?: "SIN CUENTA",
                        color = if (account != null) BedrockText else BedrockGold,
                        style = MaterialTheme.typography.labelMedium
                    )
                    PixelText(
                        text = account?.type?.label ?: "Offline",
                        color = BedrockTextDim,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                // Datos: sesión, GCoins y nivel.
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatChip(
                            label = "Sesión",
                            value = when (account?.type) {
                                AccountType.MICROSOFT -> "online"
                                null -> "—"
                                else -> "offline"
                            },
                            tint = BedrockGreen
                        )
                        StatChip(label = "GCoins", value = "0", tint = BedrockGold)
                        StatChip(label = "Nivel", value = "1", tint = BedrockText)
                    }

                    TabPill("INFO", active = tab == 0) { tab = 0 }
                    TabPill("LOGROS", active = tab == 1) { tab = 1 }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 132.dp)
                            .background(BedrockVoid)
                            .border(1.dp, BedrockBorderSoft)
                            .padding(9.dp)
                    ) {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            if (tab == 0) {
                                PixelText(
                                    text = "Descripción",
                                    color = BedrockTextDim,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                PixelText(
                                    text = if (account == null) {
                                        "Crea un perfil offline para empezar a jugar."
                                    } else {
                                        "Perfil ${account.type.label.lowercase()} de GLauncher. " +
                                            "Juega Minecraft $selectedVersion con el mando virtual."
                                    },
                                    color = BedrockText,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(Modifier.height(3.dp))
                                KeyValue("Versión activa", "Minecraft $selectedVersion")
                                KeyValue("Motor", "Java por versión (8 / 17 / 21)")
                                KeyValue("Racha", "${streak.current} días")
                            } else {
                                KeyValue("Racha actual", "${streak.current} días")
                                KeyValue("Récord", "${streak.longest} días")
                                KeyValue("Días firmados", "${streak.total}")
                                Spacer(Modifier.height(3.dp))
                                PixelText(
                                    text = "Últimos 7 días",
                                    color = BedrockTextDim,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    streak.days.forEach { firmado ->
                                        Box(
                                            Modifier
                                                .size(16.dp)
                                                .background(if (firmado) BedrockGreen else BedrockRow)
                                                .border(1.dp, BedrockBorderSoft)
                                        )
                                    }
                                }
                            }
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
                    text = "CUENTAS",
                    onClick = {
                        onClose()
                        onOpenAccounts()
                    },
                    tone = McButtonTone.Ghost,
                    icon = IconHead,
                    modifier = Modifier.weight(1f),
                    height = 36.dp
                )
                McButton(
                    text = "VOLVER",
                    onClick = onClose,
                    tone = McButtonTone.Primary,
                    icon = IconBack,
                    modifier = Modifier.weight(1f),
                    height = 36.dp
                )
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, tint: Color) {
    Column(
        modifier = Modifier
            .background(BedrockRow)
            .border(1.dp, BedrockBorderSoft)
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        PixelText(
            text = label,
            color = BedrockTextDim,
            style = MaterialTheme.typography.labelSmall
        )
        PixelText(
            text = value,
            color = tint,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun TabPill(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp)
            .background(if (active) BedrockRow else BedrockPanel)
            .border(1.dp, if (active) BedrockGold else BedrockBorderSoft)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        PixelText(
            text = text,
            color = if (active) BedrockGold else BedrockTextDim,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        PixelText(
            text = key,
            color = BedrockTextDim,
            style = MaterialTheme.typography.labelSmall
        )
        PixelText(
            text = value,
            color = BedrockText,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
