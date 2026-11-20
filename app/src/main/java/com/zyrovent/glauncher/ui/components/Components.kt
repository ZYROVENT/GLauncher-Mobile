package com.zyrovent.glauncher.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyrovent.glauncher.R
import com.zyrovent.glauncher.ui.theme.BedrockBar
import com.zyrovent.glauncher.ui.theme.BedrockBorder
import com.zyrovent.glauncher.ui.theme.BedrockBorderSoft
import com.zyrovent.glauncher.ui.theme.BedrockGold
import com.zyrovent.glauncher.ui.theme.BedrockGreen
import com.zyrovent.glauncher.ui.theme.BedrockHighlight
import com.zyrovent.glauncher.ui.theme.BedrockPanel
import com.zyrovent.glauncher.ui.theme.BedrockRow
import com.zyrovent.glauncher.ui.theme.BedrockText
import com.zyrovent.glauncher.ui.theme.BedrockTextDim
import com.zyrovent.glauncher.ui.theme.BedrockVoid
import com.zyrovent.glauncher.ui.theme.Minecraftia
import com.zyrovent.glauncher.ui.theme.TextMuted
import com.zyrovent.glauncher.ui.theme.TextPrimary

fun wallpaperRes(index: Int): Int = when (index) {
    1 -> R.drawable.bg_1
    3 -> R.drawable.bg_3
    4 -> R.drawable.bg_4
    5 -> R.drawable.bg_5
    else -> R.drawable.bg_2
}

/** Sombra dura de un píxel, como el texto de Minecraft. */
val PixelShadow = Shadow(color = Color.Black.copy(alpha = 0.8f), offset = Offset(2f, 2f), blurRadius = 0f)

@Composable
fun PixelText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = BedrockText,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    maxLines: Int = 1
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = style.copy(shadow = PixelShadow),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

/** Fondo: la imagen del launcher muy oscurecida, como el difuminado de Bedrock. */
@Composable
fun GLauncherWallpaper(index: Int, modifier: Modifier = Modifier) {
    val veil = remember {
        Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.62f),
            0.5f to Color.Black.copy(alpha = 0.70f),
            1f to Color.Black.copy(alpha = 0.82f)
        )
    }
    Box(modifier.fillMaxSize()) {
        Image(
            painter = painterResource(wallpaperRes(index)),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )
        Box(Modifier.matchParentSize().background(veil))
    }
}

/**
 * Ventana de Bedrock: borde claro de 2dp, fondo casi negro y cabecera con el
 * título centrado. Todo lo demás se dibuja dentro.
 */
@Composable
fun BedrockWindow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: PixelArt? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    fillHeight: Boolean = false,
    contentModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .clip(RectangleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .border(2.dp, BedrockBorder)
            .padding(2.dp)
            .background(BedrockPanel.copy(alpha = 0.97f))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(BedrockBar)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                PixelIcon(
                    art = icon,
                    size = 15.dp,
                    tint = BedrockHighlight,
                    accent = BedrockGreen,
                    modifier = Modifier.padding(end = 7.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                PixelText(
                    text = title,
                    color = BedrockText,
                    style = MaterialTheme.typography.titleMedium
                )
                if (subtitle != null) {
                    PixelText(
                        text = subtitle,
                        color = BedrockTextDim,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            trailing?.invoke(this)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(BedrockVoid))
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (fillHeight) Modifier.weight(1f) else Modifier)
                .background(BedrockVoid.copy(alpha = 0.35f))
                .padding(10.dp)
                .then(contentModifier),
            verticalArrangement = Arrangement.spacedBy(7.dp),
            content = content
        )
    }
}

/** Caja interior de Bedrock: filo claro de 1dp y fondo gris oscuro. */
@Composable
fun McPanel(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(10.dp),
    borderColor: Color = BedrockBorderSoft,
    background: Color = BedrockRow.copy(alpha = 0.85f),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RectangleShape)
            .background(background)
            .border(1.dp, borderColor)
            .padding(padding),
        content = content
    )
}

/**
 * Fila de opción de los ajustes: etiqueta a la izquierda y control a la derecha,
 * separada por una línea de 1dp. Es el patrón de todas las listas de Bedrock.
 */
@Composable
fun BedrockOptionRow(
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(BedrockRow.copy(alpha = 0.55f))
            .border(1.dp, BedrockBorderSoft.copy(alpha = 0.7f))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            PixelText(text = label, color = BedrockText, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                PixelText(
                    text = description,
                    color = BedrockTextDim.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2
                )
            }
        }
        trailing()
    }
}

enum class McButtonTone { Primary, Success, Ghost, Danger }

/**
 * Botón de Minecraft: cara con degradado, marco negro de 2dp, filo claro arriba y
 * texto con sombra. Se oscurece al pulsarlo en vez de escalar.
 */
@Composable
fun McButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: McButtonTone = McButtonTone.Primary,
    icon: PixelArt? = null,
    height: Dp = 40.dp
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val alpha by animateFloatAsState(targetValue = if (enabled) 1f else 0.4f, label = "btnAlpha")

    val face = when (tone) {
        McButtonTone.Primary -> listOf(Color(0xFF848484), Color(0xFF545454))
        McButtonTone.Success -> listOf(Color(0xFF6FC25D), Color(0xFF417F36))
        McButtonTone.Danger -> listOf(Color(0xFFC55F5F), Color(0xFF843636))
        McButtonTone.Ghost -> listOf(Color(0xFF454545), Color(0xFF2A2A2A))
    }

    Box(
        modifier = modifier
            .height(height)
            .graphicsLayer { this.alpha = alpha }
            .background(Color(0xFF070707))
            .border(1.dp, BedrockBorder)
            .padding(1.dp)
            .background(Brush.verticalGradient(if (pressed) listOf(face[1], face[1]) else face))
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Relieve de Minecraft: filo claro arriba y sombra abajo.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = if (pressed) 0.06f else 0.24f))
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(2.dp)
                .background(Color.Black.copy(alpha = 0.34f))
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.graphicsLayer { translationY = if (pressed) 1.5f else 0f }
        ) {
            if (icon != null) {
                PixelIcon(
                    art = icon,
                    size = 14.dp,
                    tint = BedrockText,
                    accent = BedrockGreen,
                    modifier = Modifier.padding(end = 6.dp)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(shadow = PixelShadow),
                color = BedrockText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Etiqueta de estado con marco: verde activo, gris inactivo. */
@Composable
fun McTag(text: String, color: Color = BedrockGreen, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(BedrockVoid.copy(alpha = 0.55f))
            .border(1.dp, color.copy(alpha = 0.55f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(shadow = PixelShadow),
            color = color,
            maxLines = 1
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(shadow = PixelShadow),
        color = BedrockTextDim,
        modifier = modifier,
        maxLines = 1
    )
}

@Composable
fun PixelDivider(modifier: Modifier = Modifier, color: Color = BedrockBorderSoft) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

@Composable
fun InfoRow(label: String, value: String, valueColor: Color = BedrockTextDim) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = BedrockTextDim,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(shadow = PixelShadow),
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Casilla cuadrada de Bedrock: verde con marca al estar activa. */
@Composable
fun BedrockToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val fill by animateColorAsState(
        targetValue = if (checked) BedrockGreen else BedrockBar,
        animationSpec = tween(140),
        label = "toggleFill"
    )
    Box(
        Modifier
            .size(24.dp)
            .background(BedrockVoid)
            .border(2.dp, Color(0xFF141414))
            .padding(2.dp)
            .background(fill)
            .border(1.dp, if (checked) Color.White.copy(alpha = 0.25f) else BedrockBorder)
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            // Marca dibujada a mano: dos trazos de 2dp, sin depender de iconos.
            Box(Modifier.size(11.dp)) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 1.dp, bottom = 2.dp)
                        .size(width = 2.dp, height = 6.dp)
                        .graphicsLayer { rotationZ = -45f }
                        .background(Color.White)
                )
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 1.dp, top = 1.dp)
                        .size(width = 2.dp, height = 10.dp)
                        .graphicsLayer { rotationZ = 40f }
                        .background(Color.White)
                )
            }
        }
    }
}

/** Selector «◀ valor ▶» de los ajustes de Bedrock. */
@Composable
fun BedrockSelector(
    value: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier
            .height(32.dp)
            .background(BedrockBar)
            .border(1.dp, BedrockBorder)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(24.dp).clickable(enabled = enabled) { onPrevious() },
            contentAlignment = Alignment.Center
        ) {
            Text("◀", color = if (enabled) BedrockText else BedrockTextDim, fontSize = 14.sp)
        }
        Text(
            text = value,
            color = BedrockText,
            style = MaterialTheme.typography.labelMedium.copy(shadow = PixelShadow),
            modifier = Modifier.widthIn(min = 74.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Box(
            Modifier.size(24.dp).clickable(enabled = enabled) { onNext() },
            contentAlignment = Alignment.Center
        ) {
            Text("▶", color = if (enabled) BedrockText else BedrockTextDim, fontSize = 14.sp)
        }
    }
}

/** Barra de progreso de Bedrock: relleno claro sobre pista hundida. */
@Composable
fun BedrockProgress(progress: Float, modifier: Modifier = Modifier, height: Dp = 14.dp) {
    Box(
        modifier
            .height(height)
            .background(BedrockVoid.copy(alpha = 0.7f))
            .border(1.dp, BedrockBorder)
            .padding(2.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxSize()
                .background(BedrockGreen)
        )
    }
}

@Composable
fun BedrockSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        steps = steps.coerceAtLeast(0),
        modifier = modifier.height(28.dp),
        colors = SliderDefaults.colors(
            thumbColor = BedrockHighlight,
            activeTrackColor = BedrockHighlight,
            inactiveTrackColor = BedrockVoid,
            activeTickColor = BedrockBar,
            inactiveTickColor = BedrockBorder
        )
    )
}

/** Pestaña del menú lateral de Bedrock: fila que se ilumina al seleccionarla. */
@Composable
fun BedrockTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: PixelArt? = null,
    badge: String? = null
) {
    val background by animateColorAsState(
        targetValue = if (selected) BedrockRow else Color.Transparent,
        animationSpec = tween(120),
        label = "tabBg"
    )
    Row(
        modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(background)
            .border(1.dp, if (selected) BedrockBorder else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(16.dp)
                .background(if (selected) BedrockGreen else Color.Transparent)
        )
        Spacer(Modifier.width(8.dp))
        if (icon != null) {
            PixelIcon(
                art = icon,
                size = 13.dp,
                tint = if (selected) BedrockText else BedrockTextDim,
                accent = BedrockGreen,
                modifier = Modifier.padding(end = 7.dp)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(shadow = PixelShadow),
            color = if (selected) BedrockText else BedrockTextDim,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (badge != null) {
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall,
                color = BedrockGold,
                maxLines = 1
            )
        }
    }
}

@Composable
fun McTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    fontFamily: FontFamily = Minecraftia,
    fontSize: Int = 14,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    // Mismo `BasicTextField` que usa el login (que sí abre el teclado): el
    // `OutlinedTextField` de Material3 de esta versión se quedaba sin foco al
    // tocarlo dentro de las ventanas y no dejaba escribir. El borde y el
    // placeholder se dibujan a mano en el `decorationBox`, así se ve igual.
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val textStyle = MaterialTheme.typography.bodyLarge.copy(
        fontFamily = fontFamily,
        fontSize = fontSize.sp,
        color = BedrockText
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else Int.MAX_VALUE,
        interactionSource = interaction,
        visualTransformation = visualTransformation,
        textStyle = textStyle,
        cursorBrush = SolidColor(BedrockHighlight),
        modifier = modifier
            .fillMaxWidth()
            .background(BedrockVoid.copy(alpha = if (focused) 0.8f else 0.65f))
            .border(1.dp, if (focused) BedrockBorder else BedrockBorderSoft)
            .padding(horizontal = 14.dp, vertical = 16.dp),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = textStyle,
                        color = BedrockTextDim.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                inner()
            }
        }
    )
}

/** Campo de una sola línea compacto: altura configurable con soporte de placeholder y visualTransformation. */
@Composable
fun McCompactField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fontSize: Int = 13,
    height: Dp = 40.dp,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        interactionSource = interaction,
        visualTransformation = visualTransformation,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = Minecraftia,
            fontSize = fontSize.sp,
            color = BedrockText
        ),
        cursorBrush = SolidColor(BedrockHighlight),
        modifier = modifier
            .height(height)
            .background(BedrockVoid.copy(alpha = if (enabled) 0.85f else 0.45f))
            .border(1.dp, if (focused) BedrockGreen else BedrockBorderSoft),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = Minecraftia,
                            fontSize = fontSize.sp
                        ),
                        color = BedrockTextDim.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                inner()
            }
        }
    )
}

@Composable
fun PlayerAvatar(size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .background(BedrockVoid.copy(alpha = 0.6f))
            .border(1.dp, BedrockBorderSoft)
            .padding(3.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.avatar_default),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun ScreenHeader(title: String, subtitle: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            PixelText(text = title, color = BedrockText, style = MaterialTheme.typography.headlineMedium)
            if (subtitle != null) {
                PixelText(
                    text = subtitle,
                    color = BedrockTextDim,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2
                )
            }
        }
        action?.invoke()
    }
}

@Composable
fun VSpace(height: Dp) = Spacer(Modifier.height(height))

@Composable
fun HSpace(width: Dp) = Spacer(Modifier.width(width))

// Compatibilidad con pantallas antiguas.
val TextPrimaryLegacy = TextPrimary
val TextMutedLegacy = TextMuted
