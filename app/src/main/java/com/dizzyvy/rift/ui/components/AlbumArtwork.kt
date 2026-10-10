package com.dizzyvy.rift.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import com.dizzyvy.rift.music.artwork.ArtworkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.net.Uri
import com.dizzyvy.rift.ui.theme.RiftPalette

@Composable
fun AlbumArtwork(uri: Uri?, title: String, repository: ArtworkRepository?, modifier: Modifier = Modifier, fallbackInitial: Boolean = false) {
    val artwork = produceState<android.graphics.Bitmap?>(null, uri, repository) {
        value = if (uri != null && repository != null) withContext(Dispatchers.IO) { repository.load(uri)?.bitmap } else null
    }.value
    Box(modifier.clip(if (fallbackInitial) CircleShape else RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
        if (artwork != null) {
            Image(artwork.asImageBitmap(), contentDescription = "$title album artwork", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        } else {
            val palette = listOf(RiftPalette.lavender, RiftPalette.dustyRose, RiftPalette.sage, RiftPalette.butter, Color(0xFFFAF9F7))
            val color = palette[(title.hashCode().toUInt().toLong() % palette.size).toInt()]
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(color, RiftPalette.surface)))) {
                Canvas(Modifier.fillMaxSize()) {
                    val scale = size.minDimension
                    when (title.hashCode().ushr(3) % 4) {
                        0 -> {
                            drawCircle(RiftPalette.periwinkle.copy(alpha = 0.75f), radius = scale * 0.48f, center = Offset(size.width * 0.82f, size.height * 0.18f))
                            drawCircle(RiftPalette.dustyRose.copy(alpha = 0.72f), radius = scale * 0.35f, center = Offset(size.width * 0.15f, size.height * 0.88f))
                        }
                        1 -> {
                            drawArc(RiftPalette.periwinkle, 190f, 160f, false, Offset(size.width * 0.10f, size.height * 0.10f), Size(scale * 0.85f, scale * 0.85f), style = Stroke(scale * 0.15f))
                            drawCircle(RiftPalette.sage, radius = scale * 0.18f, center = Offset(size.width * 0.76f, size.height * 0.77f))
                        }
                        2 -> {
                            drawRect(RiftPalette.dustyRose.copy(alpha = 0.85f), Offset(0f, size.height * 0.56f), Size(size.width, size.height * 0.44f))
                            drawCircle(RiftPalette.butter, radius = scale * 0.24f, center = Offset(size.width * 0.72f, size.height * 0.25f))
                        }
                        else -> {
                            drawCircle(RiftPalette.sage.copy(alpha = 0.8f), radius = scale * 0.42f, center = Offset(size.width * 0.5f, size.height * 0.5f))
                            drawCircle(color, radius = scale * 0.23f, center = Offset(size.width * 0.5f, size.height * 0.5f))
                        }
                    }
                }
                if (fallbackInitial) {
                    Text(title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?", modifier = Modifier.align(Alignment.Center), color = RiftPalette.charcoal, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                }
            }
        }
    }
}
