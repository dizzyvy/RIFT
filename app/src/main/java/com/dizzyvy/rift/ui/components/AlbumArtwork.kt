package com.dizzyvy.rift.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import com.dizzyvy.rift.music.artwork.ArtworkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.net.Uri

private val FALLBACK_ARTWORK_COLORS = listOf(
    Color(0xFFE95865),
    Color(0xFF438CCD),
    Color(0xFFFFC833),
    Color(0xFF50A982),
    Color(0xFF9B75BC),
)

@Composable
fun AlbumArtwork(
    uri: Uri?,
    title: String,
    repository: ArtworkRepository?,
    modifier: Modifier = Modifier,
    fallbackInitial: Boolean = false,
    contentScale: ContentScale = ContentScale.Crop,
    sourceDateModifiedSeconds: Long? = null,
    cornerRadius: androidx.compose.ui.unit.Dp = 12.dp,
) {
    BoxWithConstraints(modifier.clip(if (fallbackInitial) CircleShape else RoundedCornerShape(cornerRadius)), contentAlignment = Alignment.Center) {
        val maxDimensionPx = with(LocalDensity.current) {
            maxOf(maxWidth, maxHeight).coerceAtMost(512.dp).roundToPx().coerceAtLeast(1)
        }
        val artwork = produceState<android.graphics.Bitmap?>(
            null,
            uri,
            repository,
            maxDimensionPx,
            sourceDateModifiedSeconds,
        ) {
            value = if (uri != null && repository != null) {
                withContext(Dispatchers.IO) {
                    repository.load(uri, maxDimensionPx, sourceDateModifiedSeconds)?.bitmap
                }
            } else null
        }.value
        if (artwork != null) {
            Image(artwork.asImageBitmap(), contentDescription = "$title album artwork", modifier = Modifier.fillMaxSize(), contentScale = contentScale)
        } else {
            val color = FALLBACK_ARTWORK_COLORS[(title.hashCode().toUInt().toLong() % FALLBACK_ARTWORK_COLORS.size).toInt()]
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(color, color.copy(alpha = .65f)))), contentAlignment = Alignment.Center) {
                if (fallbackInitial) {
                    Text(title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.fillMaxSize(0.45f), tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}
