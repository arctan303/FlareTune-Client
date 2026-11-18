package com.arcinks.flaretune.ui

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import com.arcinks.flaretune.playback.buildCoverImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal val WideMiniPlayerFallbackTone = Color(0xFF333740)
private val WideMiniPlayerInk = Color(0xFF13151B)

internal fun Modifier.wideMiniPlayerBackdrop(tone: State<Color>): Modifier = drawWithCache {
    val leftToRight = layoutDirection == LayoutDirection.Ltr
    val edge = tone.value
    val brush = Brush.linearGradient(
        colors = listOf(edge, lerp(WideMiniPlayerInk, edge, 0.7f)),
        start = Offset(if (leftToRight) 0f else size.width, 0f),
        end = Offset(if (leftToRight) size.width else 0f, size.height * 0.32f),
    )
    onDrawBehind { drawRect(brush) }
}

@Composable
internal fun WideMiniPlayerArtwork(
    coverUrl: String?,
    loader: ImageLoader?,
    artworkSize: Dp,
    cachedTone: Color?,
    onCoverTone: (String, Color?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reportTone by rememberUpdatedState(onCoverTone)
    val toneKnown by rememberUpdatedState(cachedTone != null)
    val targetPx = quantizeArtworkDecodePx(with(LocalDensity.current) { artworkSize.roundToPx() })
    Box(
        modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithCache {
                val leftToRight = layoutDirection == LayoutDirection.Ltr
                val mask = Brush.horizontalGradient(
                    0f to Color.Black,
                    0.58f to Color.Black,
                    0.68f to Color.Black.copy(alpha = 0.93f),
                    0.81f to Color.Black.copy(alpha = 0.6f),
                    0.93f to Color.Black.copy(alpha = 0.2f),
                    1f to Color.Transparent,
                    startX = if (leftToRight) 0f else size.width,
                    endX = if (leftToRight) size.width else 0f,
                )
                onDrawWithContent {
                    drawContent()
                    drawRect(mask, blendMode = BlendMode.DstIn)
                }
            }
            .background(Color(0xFF343740)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(ClientIcons.Music, contentDescription = null, modifier = Modifier.size(26.dp), tint = Color.White.copy(alpha = 0.72f))
        if (loader != null && !coverUrl.isNullOrBlank()) {
            val request = remember(context, coverUrl, targetPx, loader) {
                buildCoverImageRequest(context, coverUrl, targetPx, loader, allowHardware = false)
            }
            AsyncImage(
                model = request,
                imageLoader = loader,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { result ->
                    if (!toneKnown) {
                        val bitmap = (result.result.drawable as? BitmapDrawable)?.bitmap
                        scope.launch {
                            val tone = withContext(Dispatchers.Default) { bitmap?.let(::sampleWideMiniPlayerTone) }
                            reportTone(coverUrl, tone)
                        }
                    }
                },
                onError = { if (!toneKnown) reportTone(coverUrl, null) },
            )
        }
    }
}

internal fun sampleWideMiniPlayerTone(bitmap: Bitmap): Color? {
    if (bitmap.isRecycled || (Build.VERSION.SDK_INT >= 26 && bitmap.config == Bitmap.Config.HARDWARE)) return null
    val side = minOf(bitmap.width, bitmap.height)
    val left = (bitmap.width - side) / 2
    val top = (bitmap.height - side) / 2
    val columns = 16
    val rows = 16
    var redSum = 0f
    var greenSum = 0f
    var blueSum = 0f
    var weightSum = 0f
    for (row in 0 until rows) {
        val y = top + (side * (row + 0.5f) / rows).toInt()
        for (column in 0 until columns) {
            // Sample the visible square, focusing on the part that meets the player background.
            val x = left + (side * (0.24f + 0.76f * (column + 0.5f) / columns)).toInt()
            val pixel = bitmap.getPixel(x, y)
            if (pixel ushr 24 < 128) continue
            val red = pixel ushr 16 and 255
            val green = pixel ushr 8 and 255
            val blue = pixel and 255
            val highest = maxOf(red, green, blue)
            val lowest = minOf(red, green, blue)
            val weight = if (highest < 14 || lowest > 239) 0.2f else 0.8f + (highest - lowest) / 510f
            redSum += red * weight
            greenSum += green * weight
            blueSum += blue * weight
            weightSum += weight
        }
    }
    if (weightSum == 0f) return null
    val sampled = Color(redSum / weightSum / 255f, greenSum / weightSum / 255f, blueSum / weightSum / 255f)
    val edge = lerp(WideMiniPlayerInk, sampled, 0.51f)
    // Bound the brightness so the light lyric and metadata colors remain legible through the transition.
    val scale = minOf(1f, 0.38f / maxOf(edge.red, edge.green, edge.blue))
    return Color(edge.red * scale, edge.green * scale, edge.blue * scale)
}
