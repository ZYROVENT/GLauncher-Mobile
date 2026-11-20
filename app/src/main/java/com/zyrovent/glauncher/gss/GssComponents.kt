package com.zyrovent.glauncher.gss

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zyrovent.glauncher.ui.components.IconStar
import com.zyrovent.glauncher.ui.components.PixelIcon
import com.zyrovent.glauncher.ui.components.PixelText

/**
 * Contenedor Composable que renderiza componentes interactivos de GSS spawneados
 * dentro de un target específico (ej: ".card", ".view-content", "header", ".modal-content").
 */
@Composable
fun BoxScope.GssSpawnContainer(target: String) {
    val spawns = GssEngine.getSpawnedForTarget(target)
    if (spawns.isEmpty()) return

    for (comp in spawns) {
        val alignment = when (comp.move) {
            "top-left" -> Alignment.TopStart
            "top-right" -> Alignment.TopEnd
            "bottom-left" -> Alignment.BottomStart
            "bottom-right" -> Alignment.BottomEnd
            else -> Alignment.Center
        }

        val shape = RoundedCornerShape(comp.cornerRadius)
        val bg = comp.bg ?: Color.Transparent
        val border = comp.border

        Box(
            modifier = Modifier
                .align(alignment)
                .offset(x = comp.moveX, y = comp.moveY)
                .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
                .clip(shape)
                .background(bg)
                .clickable {
                    comp.onClickAction?.let { action ->
                        GssEngine.triggerAction(action, comp.sound)
                    }
                }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (comp.icon.isNotBlank()) {
                    PixelIcon(art = IconStar, size = 12.dp, tint = comp.textColor ?: Color.White)
                    Spacer(Modifier.width(4.dp))
                }
                if (comp.text.isNotBlank()) {
                    PixelText(
                        text = comp.text,
                        color = comp.textColor ?: Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
