package com.dizzyvy.rift.ui.player

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2

@Composable
fun ClickWheel(
    playing: Boolean,
    queueOpen: Boolean,
    onMenu: () -> Unit,
    onPrevious: () -> Unit,
    onTogglePlayback: () -> Unit,
    onNext: () -> Unit,
    onSelect: () -> Unit,
    onRotate: (Float) -> Unit,
    sensitivity: Float = 1f,
    hapticsEnabled: Boolean = true,
    compact: Boolean = false,
) {
    val view = LocalView.current
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val wheelColor = if (isDarkTheme) androidx.compose.ui.graphics.Color(0xFF202833) else androidx.compose.ui.graphics.Color(0xFFE8E5DF)
    val wheelInsetColor = if (isDarkTheme) androidx.compose.ui.graphics.Color(0xFF303A47) else androidx.compose.ui.graphics.Color(0xFFF5F3EE)
    val wheelOutlineColor = if (isDarkTheme) androidx.compose.ui.graphics.Color(0xFF425165) else androidx.compose.ui.graphics.Color(0xFFD4D0C8)
    val centerRadius = with(LocalDensity.current) { (if (compact) 44.dp else 54.dp).toPx() }
    val latestOnRotate = rememberUpdatedState(onRotate)
    val haptics = remember(view, latestOnRotate, sensitivity, hapticsEnabled) {
        HapticDetents(view, sensitivity.coerceIn(0.5f, 2f), hapticsEnabled) { latestOnRotate.value(it) }
    }
    val wheelSize = if (compact) 208.dp else 270.dp
    Box(Modifier.fillMaxWidth().height(if (compact) 220.dp else 286.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(wheelSize).clip(CircleShape)
                .background(wheelColor)
                .border(1.dp, wheelOutlineColor, CircleShape)
                .pointerInput(queueOpen) {
                    var previousAngle: Float? = null
                    var rotationEnabled = false
                    detectDragGestures(onDragStart = { point -> rotationEnabled = radius(point, size.width, size.height) > centerRadius; previousAngle = if (rotationEnabled) angle(point, size.width, size.height) else null }, onDragEnd = { previousAngle = null; rotationEnabled = false }, onDragCancel = { previousAngle = null; rotationEnabled = false }) { change, _ ->
                        val current = angle(change.position, size.width, size.height)
                        if (rotationEnabled) previousAngle?.let { old ->
                            var delta = current - old
                            if (delta > 180f) delta -= 360f
                            if (delta < -180f) delta += 360f
                            haptics.add(delta)
                        }
                        if (rotationEnabled) previousAngle = current
                        change.consume()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // A second inset disc gives the wheel a clean inner track; the center is a plain SELECT control.
            Box(Modifier.fillMaxSize().padding(if (compact) 28.dp else 36.dp).clip(CircleShape).background(wheelInsetColor))
            WheelLabel("MENU", Modifier.align(Alignment.TopCenter).padding(top = if (compact) 13.dp else 19.dp), onMenu)
            WheelLabel("|◀", Modifier.align(Alignment.CenterStart).padding(start = if (compact) 11.dp else 15.dp), onPrevious)
            WheelLabel("▶|", Modifier.align(Alignment.CenterEnd).padding(end = if (compact) 11.dp else 15.dp), onNext)
            WheelLabel(if (playing) "❚❚" else "▶", Modifier.align(Alignment.BottomCenter).padding(bottom = if (compact) 13.dp else 19.dp), onTogglePlayback)
            Surface(onClick = onSelect, modifier = Modifier.size(if (compact) 82.dp else 104.dp).semantics { contentDescription = if (queueOpen) "Close Up Next" else "Open Up Next" }, shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                Box(contentAlignment = Alignment.Center) { Text("SELECT", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun WheelLabel(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.sizeIn(minWidth = 58.dp, minHeight = 42.dp).clip(CircleShape).clickable(onClick = onClick).semantics { contentDescription = when (label) { "MENU" -> "Menu"; "|◀" -> "Previous track"; "▶|" -> "Next track"; else -> "Playback" } }, contentAlignment = Alignment.Center) {
        Text(label, fontSize = if (label == "MENU") 11.sp else 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

private class HapticDetents(private val view: android.view.View, private val sensitivity: Float, private val enabled: Boolean, private val rotate: (Float) -> Unit) {
    private var accumulated = 0f
    fun add(degrees: Float) {
        accumulated += degrees
        if (enabled) {
            while (accumulated >= 15f) { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); accumulated -= 15f }
            while (accumulated <= -15f) { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); accumulated += 15f }
        } else {
            accumulated = 0f
        }
        rotate(degrees / 360f * sensitivity)
    }
}

private fun angle(point: Offset, width: Int, height: Int): Float = Math.toDegrees(
    atan2((point.y - height / 2f).toDouble(), (point.x - width / 2f).toDouble()),
).toFloat()

private fun radius(point: Offset, width: Int, height: Int): Float {
    val x = point.x - width / 2f
    val y = point.y - height / 2f
    return kotlin.math.sqrt(x * x + y * y)
}
