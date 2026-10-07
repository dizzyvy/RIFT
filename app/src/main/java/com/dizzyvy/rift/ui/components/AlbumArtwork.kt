package com.dizzyvy.rift.ui.components

import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

@Composable
fun AlbumArtwork(uri: Uri?, title: String, repository: ArtworkRepository?, modifier: Modifier = Modifier, fallbackInitial: Boolean = false) {
    val artwork = produceState<android.graphics.Bitmap?>(null, uri, repository) {
        value = if (uri != null && repository != null) withContext(Dispatchers.IO) { repository.load(uri)?.bitmap } else null
    }.value
    Box(modifier.clip(if (fallbackInitial) CircleShape else RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
        if (artwork != null) {
            Image(artwork.asImageBitmap(), contentDescription = "$title album artwork", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            val colors = listOf(Color(0xFFE95865), Color(0xFF438CCD), Color(0xFFFFC833), Color(0xFF50A982), Color(0xFF9B75BC))
            val color = colors[(title.hashCode().toUInt().toLong() % colors.size).toInt()]
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(color, color.copy(alpha = .65f)))), contentAlignment = Alignment.Center) {
                Text(if (fallbackInitial) title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?" else "♫", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            }
        }
    }
}
