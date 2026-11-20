package com.zyrovent.glauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyrovent.glauncher.data.ControlPresets
import com.zyrovent.glauncher.data.VirtualButton
import com.zyrovent.glauncher.launch.GameWindow
import com.zyrovent.glauncher.ui.theme.BedrockBorder
import com.zyrovent.glauncher.ui.theme.BedrockBorderSoft
import com.zyrovent.glauncher.ui.theme.BedrockGold
import com.zyrovent.glauncher.ui.theme.BedrockPanel
import com.zyrovent.glauncher.ui.theme.BedrockRow
import com.zyrovent.glauncher.ui.theme.BedrockText
import com.zyrovent.glauncher.ui.theme.BedrockTextDim
import com.zyrovent.glauncher.ui.theme.BedrockVoid
import kotlin.math.roundToInt

/** Colores rápidos para personalizar cada botón. */
val BUTTON_COLORS = listOf(
    0xFF2B2B2B, 0xFFE53935, 0xFF1E88E5, 0xFF43A047,
    0xFF8E24AA, 0xFFFB8C00, 0xFF00897B, 0xFF5E35B1
)

private fun codeName(button: VirtualButton): String {
    val choices = if (button.kind == VirtualButton.KIND_MOUSE) VirtualButton.MOUSE_CHOICES else VirtualButton.KEY_CHOICES
    return choices.firstOrNull { it.second == button.code }?.first ?: "KEY ${button.code}"
}

/**
 * Capa de controles: dibuja cada botón en su posición normalizada (0..1).
 * En modo edición se puede arrastrar cada uno por separado.
 */
@Composable
fun VirtualButtonPad(
    buttons: List<VirtualButton>,
    editing: Boolean,
    scale: Float,
    globalOpacity: Float,
    onMove: (VirtualButton, Float, Float) -> Unit,
    onSelect: (VirtualButton) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val density = LocalDensity.current

        buttons.forEach { button ->
            val sizeDp: Dp = (button.size * scale).dp
            val sizePx = with(density) { sizeDp.toPx() }
            val left = (button.x * widthPx) - sizePx / 2f
            val top = (button.y * heightPx) - sizePx / 2f

            key(button.id) {
                VirtualButtonKey(
                    button = button,
                    editing = editing,
                    size = sizeDp,
                    alpha = (button.opacity * globalOpacity * 1.15f).coerceIn(0.2f, 1f),
                    onDown = {
                        if (button.kind == VirtualButton.KIND_MOUSE) {
                            GameWindow.sendMouseButton(button.code, isDown = true)
                        } else {
                            GameWindow.sendKey(button.code, isDown = true)
                        }
                    },
                    onUp = {
                        if (button.kind == VirtualButton.KIND_MOUSE) {
                            GameWindow.sendMouseButton(button.code, isDown = false)
                        } else {
                            GameWindow.sendKey(button.code, isDown = false)
                        }
                    },
                    onSelect = { onSelect(button) },
                    // Se pasa el desplazamiento, no la posición final: el
                    // gesto captura un `button` viejo y si aquí se sumara a su
                    // x/y el botón volvía a su sitio en cada evento (los
                    // pequeños, directamente no se movían).
                    onDrag = { delta ->
                        onMove(button, delta.x / widthPx, delta.y / heightPx)
                    },
                    modifier = Modifier.offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                )
            }
        }
    }
}

@Composable
private fun VirtualButtonKey(
    button: VirtualButton,
    editing: Boolean,
    size: Dp,
    alpha: Float,
    onDown: () -> Unit,
    onUp: () -> Unit,
    onSelect: () -> Unit,
    onDrag: (Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    // Los callbacks se leen SIEMPRE en su versión más reciente. Antes el
    // pointerInput se quedaba con los del primer pintado y, después de editar
    // una propiedad del botón (que crea otro objeto VirtualButton), el arrastre
    // seguía llamando a los viejos: el botón dejaba de moverse hasta reiniciar.
    val currentOnDown by rememberUpdatedState(onDown)
    val currentOnUp by rememberUpdatedState(onUp)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val base = Color(button.color)
    val shape = if (button.kind == VirtualButton.KIND_MOUSE) RoundedCornerShape(50) else RoundedCornerShape(10.dp)

    val input = if (editing) {
        // Un solo gesto para arrastrar y para abrir el editor. Antes había dos
        // detectores (arrastre y toque) en el mismo nodo y el de toque se comía
        // el gesto: costaba mover el botón o abrías el editor sin querer.
        Modifier.pointerInput(button.id, editing) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume()
                var total = Offset.Zero
                var moviendo = false
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        if (!moviendo) currentOnSelect()
                        break
                    }
                    val delta = change.position - change.previousPosition
                    if (delta != Offset.Zero) {
                        total += delta
                        if (!moviendo && total.getDistance() > viewConfiguration.touchSlop) moviendo = true
                        if (moviendo) {
                            change.consume()
                            currentOnDrag(delta)
                        }
                    }
                }
            }
        }
    } else {
        Modifier.pointerInput(button.id, button.code) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    currentOnDown()
                    tryAwaitRelease()
                    pressed = false
                    currentOnUp()
                }
            )
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(if (pressed) base.copy(alpha = 1f) else base.copy(alpha = alpha))
            .border(
                width = if (editing) 2.dp else 1.dp,
                color = if (editing) BedrockGold else Color.White.copy(alpha = 0.18f),
                shape = shape
            )
            .then(input),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = button.label,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = if (button.label.length > 3) 10.sp else 13.sp,
            maxLines = 1
        )
    }
}

/** Panel para editar todas las propiedades de un botón. */
@Composable
fun VirtualButtonEditor(
    button: VirtualButton,
    onUpdate: (VirtualButton) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            // Bloquea los toques hacia el juego mientras se edita.
            .pointerInput(Unit) { detectTapGestures { } }
            // Con el teclado abierto el panel sube en vez de quedar tapado.
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(0.92f)
                // Alto relativo: la cabecera y los botones de abajo siempre se
                // ven y solo la lista de ajustes hace scroll. Antes medía 330 dp
                // fijos y en pantallas bajas (o con el teclado abierto) se
                // salían de la pantalla ELIMINAR y LISTO.
                .fillMaxHeight(0.94f)
                .clip(RoundedCornerShape(4.dp))
                .background(BedrockPanel)
                .border(2.dp, BedrockBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BedrockVoid)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PixelText(
                    text = "EDITAR «${button.label}»",
                    color = BedrockText,
                    style = MaterialTheme.typography.titleMedium
                )
                McButton("CERRAR", onClick = onClose, tone = McButtonTone.Ghost, height = 30.dp)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Texto del botón
                PixelText("TEXTO DEL BOTÓN", color = BedrockTextDim, style = MaterialTheme.typography.bodySmall)
                McTextField(
                    value = button.label,
                    onValueChange = { onUpdate(button.copy(label = it.uppercase().take(6))) },
                    placeholder = "W",
                    modifier = Modifier.fillMaxWidth()
                )

                // Asignación de tecla
                PixelText("ASIGNACIÓN", color = BedrockTextDim, style = MaterialTheme.typography.bodySmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    McButton(
                        "◀",
                        onClick = { onUpdate(cycleBinding(button, -1)) },
                        tone = McButtonTone.Primary,
                        height = 36.dp,
                        modifier = Modifier.width(52.dp)
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .background(BedrockRow)
                            .border(1.dp, BedrockBorderSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        PixelText(
                            text = codeName(button),
                            color = BedrockText,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    McButton(
                        "▶",
                        onClick = { onUpdate(cycleBinding(button, 1)) },
                        tone = McButtonTone.Primary,
                        height = 36.dp,
                        modifier = Modifier.width(52.dp)
                    )
                }

                // Tamaño
                PixelText(
                    "TAMAÑO: ${button.size.roundToInt()} dp",
                    color = BedrockTextDim,
                    style = MaterialTheme.typography.bodySmall
                )
                BedrockSlider(
                    value = button.size,
                    onValueChange = { onUpdate(button.copy(size = it)) },
                    onValueChangeFinished = {},
                    valueRange = 34f..96f
                )

                // Opacidad
                PixelText(
                    "OPACIDAD: ${(button.opacity * 100).roundToInt()}%",
                    color = BedrockTextDim,
                    style = MaterialTheme.typography.bodySmall
                )
                BedrockSlider(
                    value = button.opacity,
                    onValueChange = { onUpdate(button.copy(opacity = it)) },
                    onValueChangeFinished = {},
                    valueRange = 0.2f..1f
                )

                // Color
                PixelText("COLOR", color = BedrockTextDim, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BUTTON_COLORS.forEach { c ->
                        val selected = button.color == c
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(c))
                                .border(
                                    if (selected) 2.dp else 1.dp,
                                    if (selected) BedrockGold else BedrockBorderSoft,
                                    RoundedCornerShape(4.dp)
                                )
                                .clickable { onUpdate(button.copy(color = c)) }
                        )
                    }
                }

                // Posición fina: mover sin arrastrar, para dejar los botones alineados.
                PixelText("POSICIÓN FINA", color = BedrockTextDim, style = MaterialTheme.typography.bodySmall)
                val step = 0.01f
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    McButton("◀", onClick = {
                        onUpdate(button.copy(x = (button.x - step).coerceIn(0.02f, 0.98f)))
                    }, tone = McButtonTone.Primary, height = 34.dp, modifier = Modifier.weight(1f))
                    McButton("▶", onClick = {
                        onUpdate(button.copy(x = (button.x + step).coerceIn(0.02f, 0.98f)))
                    }, tone = McButtonTone.Primary, height = 34.dp, modifier = Modifier.weight(1f))
                    McButton("▲", onClick = {
                        onUpdate(button.copy(y = (button.y - step).coerceIn(0.03f, 0.97f)))
                    }, tone = McButtonTone.Primary, height = 34.dp, modifier = Modifier.weight(1f))
                    McButton("▼", onClick = {
                        onUpdate(button.copy(y = (button.y + step).coerceIn(0.03f, 0.97f)))
                    }, tone = McButtonTone.Primary, height = 34.dp, modifier = Modifier.weight(1f))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    McButton(
                        "CENTRAR",
                        onClick = { onUpdate(button.copy(x = 0.5f, y = 0.5f)) },
                        tone = McButtonTone.Ghost,
                        height = 34.dp,
                        modifier = Modifier.weight(1f)
                    )
                    McButton(
                        "TAMAÑO BASE",
                        onClick = { onUpdate(button.copy(size = 54f, opacity = 0.85f)) },
                        tone = McButtonTone.Ghost,
                        height = 34.dp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BedrockVoid)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                McButton(
                    "ELIMINAR",
                    onClick = onDelete,
                    tone = McButtonTone.Danger,
                    height = 38.dp,
                    modifier = Modifier.weight(1f)
                )
                McButton(
                    "DUPLICAR",
                    onClick = onDuplicate,
                    tone = McButtonTone.Primary,
                    height = 38.dp,
                    modifier = Modifier.weight(1f)
                )
                McButton(
                    "LISTO",
                    onClick = onClose,
                    tone = McButtonTone.Success,
                    height = 38.dp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun cycleBinding(button: VirtualButton, step: Int): VirtualButton {
    // Recorre las teclas normales y luego los botones del ratón.
    val all = VirtualButton.KEY_CHOICES.map { VirtualButton.KIND_KEY to it } +
        VirtualButton.MOUSE_CHOICES.map { VirtualButton.KIND_MOUSE to it }
    val current = all.indexOfFirst { it.first == button.kind && it.second.second == button.code }
    val next = all[((current + step).mod(all.size))]
    return button.copy(kind = next.first, code = next.second.second)
}

/**
 * Presets de controles: guarda el layout actual con un nombre y recupéralo
 * cuando quieras (o bórralo). Sirve para cambiar de mando entre PvP, minería,
 * construcción… sin reacomodar los botones cada vez.
 */
@Composable
fun ControlPresetsSheet(
    buttons: List<VirtualButton>,
    onLoad: (List<VirtualButton>) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val presets = remember { ControlPresets(context) }
    var names by remember { mutableStateOf(presets.names()) }
    var name by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .pointerInput(Unit) { detectTapGestures { } }
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.9f)
                .clip(RoundedCornerShape(4.dp))
                .background(BedrockPanel)
                .border(2.dp, BedrockBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BedrockVoid)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PixelText(
                    text = "PRESETS DE CONTROLES",
                    color = BedrockText,
                    style = MaterialTheme.typography.titleMedium
                )
                McButton("CERRAR", onClick = onClose, tone = McButtonTone.Ghost, height = 30.dp)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PixelText(
                    "GUARDAR EL LAYOUT ACTUAL (${buttons.size} botones)",
                    color = BedrockTextDim,
                    style = MaterialTheme.typography.bodySmall
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    McTextField(
                        value = name,
                        onValueChange = { name = it.uppercase().take(20) },
                        placeholder = "NOMBRE DEL PRESET",
                        modifier = Modifier.weight(1f)
                    )
                    McButton(
                        "GUARDAR",
                        onClick = {
                            if (name.isBlank()) {
                                status = "Escribe un nombre primero"
                            } else {
                                val saved = presets.save(name, buttons)
                                names = presets.names()
                                name = ""
                                status = "Guardado: $saved"
                            }
                        },
                        tone = McButtonTone.Success,
                        height = 40.dp
                    )
                }

                status?.let {
                    PixelText(it, color = BedrockGold, style = MaterialTheme.typography.bodySmall)
                }

                PixelDivider()
                PixelText("TUS PRESETS", color = BedrockTextDim, style = MaterialTheme.typography.bodySmall)

                if (names.isEmpty()) {
                    PixelText(
                        "Todavía no hay presets guardados.",
                        color = BedrockTextDim,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                names.forEach { presetName ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BedrockRow)
                            .border(1.dp, BedrockBorderSoft)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PixelText(
                            text = presetName,
                            color = BedrockText,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        McButton(
                            "CARGAR",
                            onClick = {
                                val loaded = presets.load(presetName)
                                if (loaded != null) {
                                    onLoad(loaded)
                                    status = "Cargado: $presetName"
                                } else {
                                    status = "El preset está dañado"
                                }
                            },
                            tone = McButtonTone.Primary,
                            height = 32.dp
                        )
                        McButton(
                            "BORRAR",
                            onClick = {
                                presets.delete(presetName)
                                names = presets.names()
                                status = "Borrado: $presetName"
                            },
                            tone = McButtonTone.Danger,
                            height = 32.dp
                        )
                    }
                }
            }
        }
    }
}

/** Hoja para añadir un botón nuevo eligiendo la tecla. */
@Composable
fun VirtualButtonAddSheet(
    onAdd: (kind: String, code: Int, label: String) -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .pointerInput(Unit) { detectTapGestures { } }
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.9f)
                .clip(RoundedCornerShape(4.dp))
                .background(BedrockPanel)
                .border(2.dp, BedrockBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BedrockVoid)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PixelText(
                    text = "AÑADIR BOTÓN",
                    color = BedrockText,
                    style = MaterialTheme.typography.titleMedium
                )
                McButton("CERRAR", onClick = onClose, tone = McButtonTone.Ghost, height = 30.dp)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PixelText("TECLADO", color = BedrockTextDim, style = MaterialTheme.typography.bodySmall)
                VirtualButton.KEY_CHOICES.chunked(5).forEach { rowKeys ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowKeys.forEach { (name, code) ->
                            McButton(
                                name,
                                onClick = { onAdd(VirtualButton.KIND_KEY, code, name) },
                                tone = McButtonTone.Primary,
                                height = 34.dp,
                                modifier = Modifier.width(84.dp)
                            )
                        }
                    }
                }

                Row(Modifier.height(6.dp)) {}

                PixelText("RATÓN", color = BedrockTextDim, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VirtualButton.MOUSE_CHOICES.forEach { (name, code) ->
                        McButton(
                            name,
                            onClick = { onAdd(VirtualButton.KIND_MOUSE, code, name) },
                            tone = McButtonTone.Success,
                            height = 34.dp,
                            modifier = Modifier.width(110.dp)
                        )
                    }
                }
            }
        }
    }
}
