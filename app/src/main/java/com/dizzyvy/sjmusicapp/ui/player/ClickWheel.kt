package com.dizzyvy.sjmusicapp.ui.player

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
) {
    val view = LocalView.current
    val centerRadius = with(LocalDensity.current) { 54.dp.toPx() }
    val latestOnRotate = rememberUpdatedState(onRotate)
    val haptics = remember(view, latestOnRotate) { HapticDetents(view) { latestOnRotate.value(it) } }
    Box(Modifier.fillMaxWidth().height(286.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(270.dp).clip(CircleShape)
                .background(androidx.compose.ui.graphics.Color(0xFFE8E5DF))
                .border(1.dp, androidx.compose.ui.graphics.Color(0xFFD4D0C8), CircleShape)
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
            Box(Modifier.fillMaxSize().padding(36.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color(0xFFF5F3EE)))
            WheelLabel("MENU", Modifier.align(Alignment.TopCenter).padding(top = 19.dp), onMenu)
            WheelLabel("|◀", Modifier.align(Alignment.CenterStart).padding(start = 15.dp), onPrevious)
            WheelLabel("▶|", Modifier.align(Alignment.CenterEnd).padding(end = 15.dp), onNext)
            WheelLabel(if (playing) "❚❚" else "▶", Modifier.align(Alignment.BottomCenter).padding(bottom = 19.dp), onTogglePlayback)
            Surface(onClick = onSelect, modifier = Modifier.size(104.dp).semantics { contentDescription = if (queueOpen) "Close Up Next" else "Open Up Next" }, shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                Box(contentAlignment = Alignment.Center) { Text("SELECT", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun WheelLabel(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.sizeIn(minWidth = 58.dp, minHeight = 42.dp).clip(CircleShape).clickable(onClick = onClick).semantics { contentDescription = when (label) { "MENU" -> "Menu"; "|◀" -> "Previous track"; "▶|" -> "Next track"; else -> "Playback" } }, contentAlignment = Alignment.Center) {
        Text(label, fontSize = if (label == "MENU") 11.sp else 16.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color(0xFF343943))
    }
}

private class HapticDetents(private val view: android.view.View, private val rotate: (Float) -> Unit) {
    private var accumulated = 0f
    fun add(degrees: Float) {
        accumulated += degrees
        while (accumulated >= 15f) { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); accumulated -= 15f }
        while (accumulated <= -15f) { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); accumulated += 15f }
        rotate(degrees / 360f)
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
