package com.zyrovent.glauncher.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zyrovent.glauncher.ui.theme.BedrockBorderSoft
import com.zyrovent.glauncher.ui.theme.BedrockGreen
import com.zyrovent.glauncher.ui.theme.BedrockRow
import com.zyrovent.glauncher.ui.theme.Gold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Caché en memoria de los iconos de Modrinth (64 mapas pequeños como mucho). */
private val iconCache = LruCache<String, Bitmap>(64)

/** Descarga un icono remoto y lo comparte entre recomposiciones. */
@Composable
fun rememberRemoteBitmap(url: String?): Bitmap? {
    val cached = url?.let { iconCache.get(it) }
    val bitmap by produceState(initialValue = cached, url) {
        if (url.isNullOrBlank()) {
            value = null
            return@produceState
        }
        iconCache.get(url)?.let {
            value = it
            return@produceState
        }
        value = withContext(Dispatchers.IO) { downloadBitmap(url) }
    }
    return bitmap
}

private fun downloadBitmap(url: String): Bitmap? = runCatching {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8_000
        readTimeout = 12_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "GLauncher/1.1 (Android)")
    }
    val bytes = try {
        if (connection.responseCode !in 200..299) return@runCatching null
        connection.inputStream.use { it.readBytes() }
    } finally {
        connection.disconnect()
    }
    if (bytes.isEmpty()) return@runCatching null

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 64) sample *= 2

    val decoded = BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample }
    ) ?: return@runCatching null

    iconCache.put(url, decoded)
    decoded
}.getOrNull()

/**
 * Icono cuadrado de un proyecto: usa la imagen de Modrinth cuando llega y, si no,
 * el icono pixel-art de reserva. Sin filtrado para que se vea nítido.
 */
@Composable
fun RemoteIcon(
    url: String?,
    size: Dp,
    fallback: PixelArt,
    modifier: Modifier = Modifier
) {
    val bitmap = rememberRemoteBitmap(url)
    Box(
        modifier
            .size(size)
            .background(BedrockRow)
            .border(1.dp, BedrockBorderSoft),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.None,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            PixelIcon(
                art = fallback,
                size = size * 0.55f,
                tint = BedrockGreen,
                accent = Gold
            )
        }
    }
}
