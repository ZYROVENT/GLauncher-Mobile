package com.zyrovent.glauncher.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zyrovent.glauncher.R

/**
 * Renderizador de Iconos Vectoriales Nativos SVG (16x16 dp).
 * Rápido, ultra nítido, sin blur y con soporte de tintado en tiempo real.
 */
@Composable
fun SvgIcon(
    @DrawableRes resId: Int,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    tint: Color = Color.White
) {
    Image(
        painter = painterResource(id = resId),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(size)
    )
}

// ==========================================
// Identificadores de recursos SVG 16x16
// ==========================================
val SvgHome = R.drawable.ic_home_16
val SvgList = R.drawable.ic_list_16
val SvgNote = R.drawable.ic_note_16
val SvgCart = R.drawable.ic_cart_16
val SvgBlock = R.drawable.ic_block_16
val SvgGear = R.drawable.ic_gear_16
val SvgHead = R.drawable.ic_head_16
val SvgChat = R.drawable.ic_chat_16
val SvgSend = R.drawable.ic_send_16
val SvgGif = R.drawable.ic_gif_16
val SvgSearch = R.drawable.ic_search_16
val SvgClose = R.drawable.ic_close_16
val SvgCheck = R.drawable.ic_check_16
val SvgStar = R.drawable.ic_star_16
val SvgBrush = R.drawable.ic_brush_16
val SvgGamepad = R.drawable.ic_gamepad_16
val SvgPlay = R.drawable.ic_play
val SvgPause = R.drawable.ic_pause
val SvgStop = R.drawable.ic_stop
val SvgSkipNext = R.drawable.ic_skip_next
val SvgSkipPrev = R.drawable.ic_skip_previous
