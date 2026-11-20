package com.zyrovent.glauncher.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.zyrovent.glauncher.ui.theme.BedrockBorderSoft
import com.zyrovent.glauncher.ui.theme.BedrockVoid

/**
 * ImageLoader optimizado con soporte para GIFs animados y estáticos.
 */
fun getImageLoader(context: android.content.Context): ImageLoader {
    return ImageLoader.Builder(context)
        .components {
            if (Build.VERSION.SDK_INT >= 28) {
                add(ImageDecoderDecoder.Factory())
            } else {
                add(GifDecoder.Factory())
            }
        }
        .crossfade(true)
        .build()
}

/**
 * Renderizador de GIFs remotos optimizado con Coil.
 * Carga de forma asíncrona sin bloquear el hilo de la UI.
 */
@Composable
fun RemoteGif(
    url: String?,
    modifier: Modifier = Modifier,
    animate: Boolean = true
) {
    val context = LocalContext.current
    val imageLoader = getImageLoader(context)

    Box(
        modifier
            .background(BedrockVoid)
            .border(1.dp, BedrockBorderSoft)
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(true)
                    .build(),
                imageLoader = imageLoader,
                contentDescription = "GIF",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Miniatura del selector de GIFs con click.
 */
@Composable
fun GifThumbnail(
    url: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageLoader = getImageLoader(context)

    Box(
        modifier
            .border(1.dp, BedrockBorderSoft)
            .clickable(onClick = onClick)
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(true)
                    .build(),
                imageLoader = imageLoader,
                contentDescription = "GIF Thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
