package com.zyrovent.glauncher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyrovent.glauncher.ui.components.BedrockWindow
import com.zyrovent.glauncher.ui.components.IconClose
import com.zyrovent.glauncher.ui.components.IconList
import com.zyrovent.glauncher.ui.components.IconTrash
import com.zyrovent.glauncher.ui.components.McButton
import com.zyrovent.glauncher.ui.components.McButtonTone
import com.zyrovent.glauncher.ui.components.McTag
import com.zyrovent.glauncher.ui.components.PixelText
import com.zyrovent.glauncher.ui.theme.BedrockBorderSoft
import com.zyrovent.glauncher.ui.theme.BedrockGreen
import com.zyrovent.glauncher.ui.theme.BedrockText
import com.zyrovent.glauncher.ui.theme.BedrockTextDim
import com.zyrovent.glauncher.ui.theme.BedrockVoid
import com.zyrovent.glauncher.ui.theme.DangerRed
import com.zyrovent.glauncher.ui.theme.Gold
import com.zyrovent.glauncher.ui.theme.Minecraftia
import kotlinx.coroutines.delay

@Composable
fun LogsOverlay(
    logs: List<String>,
    onClose: () -> Unit,
    onClear: () -> Unit,
    title: String = "Registro",
    subtitle: String? = null,
    tag: String = "BETA",
    emptyText: String = "Aún no hay actividad. Pulsa JUGAR para ver la descarga y el arranque."
) {
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.scrollToItem(logs.lastIndex)
    }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1400)
            copied = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(BedrockVoid.copy(alpha = 0.9f))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        BedrockWindow(
            title = title,
            subtitle = subtitle ?: "${logs.size} líneas del arranque",
            icon = IconList,
            modifier = Modifier.fillMaxSize(),
            trailing = {
                // Solo iconos: los textos no caben en la cabecera.
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    McButton(
                        text = "",
                        onClick = {
                            clipboard.setText(AnnotatedString(logs.joinToString("\n")))
                            copied = true
                        },
                        icon = IconList,
                        tone = if (copied) McButtonTone.Success else McButtonTone.Ghost,
                        height = 26.dp,
                        modifier = Modifier.width(34.dp)
                    )
                    McButton(
                        text = "",
                        onClick = onClear,
                        icon = IconTrash,
                        tone = McButtonTone.Ghost,
                        height = 26.dp,
                        modifier = Modifier.width(34.dp)
                    )
                    McButton(
                        text = "",
                        onClick = onClose,
                        icon = IconClose,
                        tone = McButtonTone.Danger,
                        height = 26.dp,
                        modifier = Modifier.width(34.dp)
                    )
                }
            },
            fillHeight = true
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(BedrockVoid.copy(alpha = 0.7f))
                    .border(1.dp, BedrockBorderSoft)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                if (logs.isEmpty()) {
                    PixelText(
                        text = emptyText,
                        color = BedrockTextDim,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        contentPadding = PaddingValues(bottom = 6.dp)
                    ) {
                        itemsIndexed(logs) { _, line ->
                            PixelText(
                                text = line,
                                color = when {
                                    line.contains("Error") || line.contains("No se pudo") ||
                                        line.contains("inválido") -> DangerRed
                                    line.startsWith("Detenido") -> Gold
                                    line.startsWith("  ") -> BedrockTextDim
                                    line.startsWith("GLauncher") -> BedrockGreen
                                    else -> BedrockText
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = Minecraftia,
                                    fontSize = 11.sp
                                ),
                                maxLines = 2
                            )
                        }
                    }
                }
                McTag(
                    text = tag,
                    color = BedrockTextDim,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
        }
    }
}
