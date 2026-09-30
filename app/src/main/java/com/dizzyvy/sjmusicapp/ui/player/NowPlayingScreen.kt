package com.dizzyvy.sjmusicapp.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dizzyvy.sjmusicapp.music.playback.PlaybackSnapshot

@Composable
fun NowPlayingScreen(
    playback: PlaybackSnapshot,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onPlayQueueItem: (Int) -> Unit,
) {
    val track = playback.currentTrack
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
    ) {
        TextButton(onClick = onBack, modifier = Modifier.padding(top = 6.dp)) { Text("‹  LIBRARY") }
        if (track == null) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Nothing playing", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                TextButton(onClick = onBack) { Text("Browse your music") }
            }
            return
        }
        Spacer(Modifier.height(4.dp))
        Text("NOW PLAYING", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            color = Color.Transparent,
        ) {
            Column(Modifier.background(Brush.linearGradient(listOf(Color(0xFFF05B70), Color(0xFFFFBA48), Color(0xFF53B6A0)))).padding(20.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("♫", fontSize = 112.sp, color = Color.White, fontWeight = FontWeight.Bold)
                Text(track.album.ifBlank { "SJ MUSIC" }.uppercase(), style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
        }
        Spacer(Modifier.height(22.dp))
        Text(track.title, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(track.artist.ifBlank { "Unknown artist" }, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Slider(
            value = playback.positionMs.toFloat().coerceIn(0f, playback.durationMs.coerceAtLeast(1L).toFloat()),
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..playback.durationMs.coerceAtLeast(1L).toFloat(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(playback.positionMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatTime(playback.durationMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        ClickWheel(
            playing = playback.isPlaying,
            onBack = onBack,
            onPrevious = onPrevious,
            onPlayPause = onPlayPause,
            onNext = onNext,
        )
        if (playback.errorMessage != null) Text(playback.errorMessage, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        if (playback.queue.size > 1) {
            Spacer(Modifier.height(10.dp))
            Text("UP NEXT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            playback.queue.drop(playback.currentIndex + 1).take(2).forEachIndexed { index, queued ->
                TextButton(onClick = { onPlayQueueItem(playback.currentIndex + index + 1) }, modifier = Modifier.fillMaxWidth()) {
                    Text("${queued.title}  ·  ${queued.artist.ifBlank { "Unknown artist" }}", maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ClickWheel(
    playing: Boolean,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .padding(4.dp)
                .background(Color(0xFFE9E6E0), CircleShape)
                .border(1.dp, Color(0xFFD4D0C8), CircleShape)
                .padding(12.dp)
                .background(Color(0xFFF5F3EE), CircleShape)
                .padding(12.dp)
                .background(Color(0xFFE9E6E0), CircleShape)
                .padding(7.dp)
                .background(Color(0xFFF7F5F0), CircleShape)
                .padding(54.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(onClick = onPlayPause, shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                Text(if (playing) "❚❚" else "▶", modifier = Modifier.padding(18.dp), fontSize = 22.sp, color = Color.White)
            }
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.TopCenter)) {
                Text("MENU", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF686D76))
            }
            TextButton(onClick = onPrevious, modifier = Modifier.align(Alignment.CenterStart)) {
                Text("|◀", fontSize = 17.sp, color = Color(0xFF343943))
            }
            TextButton(onClick = onNext, modifier = Modifier.align(Alignment.CenterEnd)) {
                Text("▶|", fontSize = 17.sp, color = Color(0xFF343943))
            }
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}
