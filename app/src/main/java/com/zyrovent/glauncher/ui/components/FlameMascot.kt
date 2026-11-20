package com.zyrovent.glauncher.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.zyrovent.glauncher.R
import com.zyrovent.glauncher.ui.theme.BedrockGreen
import com.zyrovent.glauncher.ui.theme.Gold
import android.graphics.BitmapFactory
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Mascota de la racha: una llama pixel art con estados de ánimo y cosméticos
 * que se dibujan aparte, como capas.
 *
 * ESTADOS (cada uno con su PNG y su animación):
 *  Idle   → `flame_idle.png` (respira) + `flame_blink.png` (parpadeo aparte)
 *  Sleepy → `dormida_anim.png` + Zzz flotando
 *  Wink   → `guino_anim.png` + chispa al lado del ojo
 *  Happy  → `feliz_anim.png` + rebote y estrellas
 *  Off    → `apagada_anim.png` (ceniza gris) + lágrimas
 *
 * COSMÉTICOS (capas transparentes encima, una por slot):
 *  Hat  → gorro de fiesta / gorro de dormir
 *  Eyes → gafas de sol
 *  Body → camiseta / bufanda
 */
enum class CosmeticSlot { Body, Eyes, Hat }

enum class Cosmetic(
    val slot: CosmeticSlot,
    @DrawableRes val resId: Int,
    val label: String
) {
    Shirt(CosmeticSlot.Body, R.drawable.cos_camiseta, "Camiseta"),
    Scarf(CosmeticSlot.Body, R.drawable.cos_bufanda, "Bufanda"),
    Sunglasses(CosmeticSlot.Eyes, R.drawable.cos_gafas, "Gafas de sol"),
    PartyHat(CosmeticSlot.Hat, R.drawable.cos_gorro_fiesta, "Gorro de fiesta"),
    NightCap(CosmeticSlot.Hat, R.drawable.cos_gorro_dormir, "Gorro de dormir")
}

/** [cosmetic] es el premio que la racha ya desbloqueó en este estado. */
enum class FlameMood(val label: String, val cosmetic: String) {
    Idle("Llama viva", "Sin cosmeticos"),
    Sleepy("Dormilona", "Bufanda"),
    Happy("Festejo", "Dia firmado!"),
    Wink("Guino", "Bufanda"),
    Cool("Cool", "Gafas de sol"),
    Party("Fiesta", "Gorro de fiesta"),
    Excited("Hito!", "Gorro de fiesta"),
    Off("Apagada", "Sin racha")
}

private const val CELL = 16

@Composable
fun FlameMascot(
    mood: FlameMood,
    cosmetics: List<Cosmetic> = emptyList(),
    modifier: Modifier = Modifier,
    size: Dp = 64.dp
) {
    // Un cosmético por slot: si llegan dos, gana el último de la lista.
    val outfit = remember(cosmetics) {
        cosmetics
            .groupBy { it.slot }
            .mapNotNull { (_, items) -> items.lastOrNull() }
            .sortedBy { it.slot.ordinal }
    }

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        val glow = when (mood) {
            FlameMood.Sleepy  -> Gold.copy(alpha = 0.12f)
            FlameMood.Happy   -> Gold.copy(alpha = 0.35f)
            FlameMood.Party   -> Color(0xFFFF9800).copy(alpha = 0.38f)
            FlameMood.Wink    -> Gold.copy(alpha = 0.22f)
            FlameMood.Cool    -> Color(0xFF00E5FF).copy(alpha = 0.25f)
            FlameMood.Excited -> Color(0xFFFFD600).copy(alpha = 0.55f)
            FlameMood.Off     -> Color(0xFF6A6A74).copy(alpha = 0.10f)
            FlameMood.Idle    -> Gold.copy(alpha = 0.20f)
        }
        Box(
            Modifier
                .size(size * 1.25f)
                .background(
                    Brush.radialGradient(
                        0f to glow,
                        0.5f to glow.copy(alpha = glow.alpha * 0.6f),
                        0.85f to glow.copy(alpha = glow.alpha * 0.15f),
                        1f to Color.Transparent
                    )
                )
        )
        when (mood) {
            FlameMood.Idle    -> IdleFlame(size, outfit)
            FlameMood.Sleepy  -> SleepyFlame(size, outfit)
            FlameMood.Wink    -> WinkFlame(size, outfit)
            FlameMood.Happy   -> HappyFlame(size, outfit)
            FlameMood.Cool    -> WinkFlame(size, outfit + Cosmetic.Sunglasses)
            FlameMood.Party   -> HappyFlame(size, outfit + Cosmetic.PartyHat)
            FlameMood.Excited -> ExcitedFlame(size, outfit + Cosmetic.PartyHat)
            FlameMood.Off     -> OffFlame(size, outfit)
        }
    }
}

// ----------------------------------------------------------------------------
// Capas: el cuerpo de la llama y, encima, cada cosmético equipado.
// ----------------------------------------------------------------------------

@Composable
private fun FlameStack(
    @DrawableRes resId: Int,
    frame: Int,
    cosmetics: List<Cosmetic>,
    modifier: Modifier = Modifier
) {
    // Los cosméticos también respiran, pero a su propio ritmo: 900 ms por frame.
    var outfitFrame by remember { mutableIntStateOf(0) }
    LaunchedEffect(cosmetics) {
        outfitFrame = 0
        while (true) {
            delay(900)
            outfitFrame = (outfitFrame + 1) % 2
        }
    }

    Box(modifier) {
        SpriteFrame(resId, frame, Modifier.fillMaxSize())
        cosmetics.forEach { cosmetic ->
            SpriteFrame(cosmetic.resId, outfitFrame, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun SpriteFrame(@DrawableRes resId: Int, frame: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sheet: ImageBitmap = remember(resId) {
        BitmapFactory.decodeResource(context.resources, resId).asImageBitmap()
    }
    Canvas(modifier) {
        drawImage(
            image = sheet,
            srcOffset = IntOffset(frame.coerceAtLeast(0) * CELL, 0),
            srcSize = IntSize(CELL, CELL),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)),
            filterQuality = FilterQuality.None
        )
    }
}

// ----------------------------------------------------------------------------
// 1. Idle: respiración lenta, punta que ondea y parpadeo independiente.
// ----------------------------------------------------------------------------

@Composable
private fun IdleFlame(size: Dp, cosmetics: List<Cosmetic>) {
    val breath = rememberInfiniteTransition(label = "idleBreath")
    val wave by breath.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3_400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "idleWave"
    )

    var frame by remember { mutableIntStateOf(0) }
    var blinking by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(230)
            frame = (frame + 1) % 4
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(4_200L + Random.nextLong(3_800))
            blinking = true
            delay(140)
            blinking = false
        }
    }

    val bob = with(LocalDensity.current) { 2.5.dp.toPx() }
    val transform = Modifier
        .size(size)
        .graphicsLayer {
            translationY = -bob * wave
            scaleX = 1f + 0.04f * wave
            scaleY = 1f + 0.05f * wave
        }

    if (blinking) {
        FlameStack(R.drawable.flame_blink, 0, cosmetics, transform)
    } else {
        FlameStack(R.drawable.flame_idle, frame, cosmetics, transform)
    }
}

// ----------------------------------------------------------------------------
// 2. Dormida: respiración profunda y tres Zzz subiendo.
// ----------------------------------------------------------------------------

@Composable
private fun SleepyFlame(size: Dp, cosmetics: List<Cosmetic>) {
    val sleep = rememberInfiniteTransition(label = "sleepBreath")
    val wave by sleep.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4_800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sleepWave"
    )
    val zzz by sleep.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5_400, easing = LinearEasing), RepeatMode.Restart),
        label = "sleepZzz"
    )

    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_700)
            frame = (frame + 1) % 2
        }
    }

    val bob = with(LocalDensity.current) { 1.dp.toPx() }

    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        FlameStack(
            resId = R.drawable.dormida_anim,
            frame = frame,
            cosmetics = cosmetics,
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    translationY = bob * wave
                    scaleY = 0.97f + 0.03f * wave
                }
        )
        Canvas(Modifier.size(size)) {
            val unit = this.size.width / 16f
            repeat(3) { i ->
                val progress = (zzz + i / 3f) % 1f
                val x = this.size.width * (0.70f + 0.06f * progress)
                val y = this.size.height * (0.30f - 0.22f * progress)
                val alpha = (1f - progress).coerceIn(0f, 1f) * 0.85f
                drawZ(Offset(x, y), unit * 0.8f, Color.White.copy(alpha = alpha))
            }
        }
    }
}

private fun DrawScope.drawZ(topLeft: Offset, cell: Float, color: Color) {
    drawRect(color, topLeft, Size(cell * 3, cell))
    drawRect(color, Offset(topLeft.x + cell * 2, topLeft.y + cell), Size(cell, cell))
    drawRect(color, Offset(topLeft.x + cell, topLeft.y + cell * 2), Size(cell, cell))
    drawRect(color, Offset(topLeft.x, topLeft.y + cell * 3), Size(cell * 3, cell))
}

// ----------------------------------------------------------------------------
// 3. Guiño: la cabeza se ladea y le sale una chispa junto al ojo abierto.
// ----------------------------------------------------------------------------

@Composable
private fun WinkFlame(size: Dp, cosmetics: List<Cosmetic>) {
    val nod = rememberInfiniteTransition(label = "winkNod")
    val wave by nod.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "winkWave"
    )

    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(480)
            frame = (frame + 1) % 2
        }
    }

    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        FlameStack(
            resId = R.drawable.guino_anim,
            frame = frame,
            cosmetics = cosmetics,
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    rotationZ = 5f * wave
                    translationY = -1f * wave
                }
        )
        Canvas(Modifier.size(size)) {
            val unit = this.size.width / 16f
            val alpha = (0.35f + 0.65f * wave).coerceIn(0f, 1f)
            drawSparkle(
                Offset(this.size.width * 0.76f, this.size.height * 0.52f),
                unit * (1.2f + 0.5f * wave),
                Gold.copy(alpha = alpha)
            )
        }
    }
}

// ----------------------------------------------------------------------------
// 4. Feliz: rebote corto y estrellas al firmar el día.
// ----------------------------------------------------------------------------

@Composable
private fun HappyFlame(size: Dp, cosmetics: List<Cosmetic>) {
    val joy = rememberInfiniteTransition(label = "happyBounce")
    val wave by joy.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(680, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "happyWave"
    )

    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(210)
            frame = (frame + 1) % 2
        }
    }

    val lift = with(LocalDensity.current) { 4.dp.toPx() }

    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        FlameStack(
            resId = R.drawable.feliz_anim,
            frame = frame,
            cosmetics = cosmetics,
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    translationY = -lift * wave
                    val s = 1f + 0.06f * wave
                    scaleX = s
                    scaleY = s
                }
        )
        Canvas(Modifier.size(size)) {
            val unit = this.size.width / 16f
            val stars = listOf(
                Offset(this.size.width * 0.12f, this.size.height * 0.22f),
                Offset(this.size.width * 0.84f, this.size.height * 0.20f),
                Offset(this.size.width * 0.93f, this.size.height * 0.58f),
                Offset(this.size.width * 0.08f, this.size.height * 0.65f),
                Offset(this.size.width * 0.52f, this.size.height * 0.10f)
            )
            stars.forEachIndexed { index, star ->
                val phase = (wave + index * 0.20f) % 1f
                val alpha = (1f - phase).coerceIn(0f, 1f)
                drawSparkle(star, unit * (1.0f + phase * 0.8f), Gold.copy(alpha = alpha))
            }
        }
    }
}

// ----------------------------------------------------------------------------
// 5. Apagada: ceniza gris, tiembla y llora.
// ----------------------------------------------------------------------------

@Composable
private fun OffFlame(size: Dp, cosmetics: List<Cosmetic>) {
    val sad = rememberInfiniteTransition(label = "offSad")
    val shiver by sad.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(280, easing = LinearEasing), RepeatMode.Reverse),
        label = "offShiver"
    )
    val tears by sad.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3_400, easing = LinearEasing), RepeatMode.Restart),
        label = "offTears"
    )

    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_500)
            frame = (frame + 1) % 2
        }
    }

    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        FlameStack(
            resId = R.drawable.apagada_anim,
            frame = frame,
            cosmetics = cosmetics,
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    translationX = (shiver * 0.8f) - 0.4f
                    scaleY = 0.98f + 0.02f * shiver
                }
        )
        Canvas(Modifier.size(size)) {
            val unit = this.size.width / 16f
            repeat(2) { i ->
                val phase = (tears + i * 0.5f) % 1f
                val x = this.size.width * (0.34f + 0.16f * i)
                val y = this.size.height * (0.60f + 0.34f * phase)
                drawRect(
                    Color(0xFF6FA8E8).copy(alpha = (1f - phase).coerceIn(0f, 1f) * 0.9f),
                    Offset(x, y),
                    Size(unit * 0.8f, unit * 1.2f)
                )
            }
        }
    }
}

// ----------------------------------------------------------------------------
// 6. Excitada: hito de racha — rebote rapido, explosion de particulas y glow.
// ----------------------------------------------------------------------------

@Composable
private fun ExcitedFlame(size: Dp, cosmetics: List<Cosmetic>) {
    val exc = rememberInfiniteTransition(label = "excitedBounce")
    val wave by exc.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "excWave"
    )
    val spin by exc.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_200, easing = LinearEasing), RepeatMode.Restart),
        label = "excSpin"
    )

    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(180)
            frame = (frame + 1) % 2
        }
    }

    val lift = with(LocalDensity.current) { 6.dp.toPx() }

    // Posiciones de los 8 destellos en circulo alrededor de la llama
    val particleAngles = remember {
        listOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f)
    }

    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        // Particulas giratorias
        Canvas(Modifier.size(size)) {
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val radius = this.size.width * 0.52f
            val unit = this.size.width / 16f
            particleAngles.forEachIndexed { i, angleDeg ->
                val angleRad = Math.toRadians((angleDeg + spin * 360f).toDouble()).toFloat()
                val px = cx + radius * kotlin.math.cos(angleRad)
                val py = cy + radius * kotlin.math.sin(angleRad)
                val phase = (spin + i / 8f) % 1f
                val alpha = 0.5f + 0.5f * kotlin.math.sin((phase * Math.PI).toFloat())
                val starSize = unit * (0.6f + 0.5f * wave)
                drawSparkle(Offset(px, py), starSize, Gold.copy(alpha = alpha))
            }
        }
        // Cuerpo de la llama con rebote rapido
        FlameStack(
            resId = R.drawable.feliz_anim,
            frame = frame,
            cosmetics = cosmetics,
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    translationY = -lift * wave
                    val s = 1f + 0.08f * wave
                    scaleX = s
                    scaleY = s
                }
        )
        // +estrella en el centro al ritmo del rebote
        Canvas(Modifier.size(size)) {
            val unit = this.size.width / 16f
            val alpha = 0.7f + 0.3f * wave
            drawSparkle(
                Offset(this.size.width * 0.5f, this.size.height * 0.18f),
                unit * (1.4f + 0.4f * wave),
                Gold.copy(alpha = alpha)
            )
        }
    }
}


/** Destello de cuatro puntas hecho con dos rectángulos cruzados. */
private fun DrawScope.drawSparkle(center: Offset, radius: Float, color: Color) {
    val arm = radius * 0.25f
    drawRect(
        color,
        Offset(center.x - radius, center.y - arm / 2f),
        Size(radius * 2f, arm)
    )
    drawRect(
        color,
        Offset(center.x - arm / 2f, center.y - radius),
        Size(arm, radius * 2f)
    )
}
