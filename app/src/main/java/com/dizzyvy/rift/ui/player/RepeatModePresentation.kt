package com.dizzyvy.rift.ui.player

import androidx.media3.common.Player

internal data class RepeatModePresentation(
    val contentDescription: String,
    val showsOneIndicator: Boolean,
)

internal fun repeatModePresentation(mode: Int): RepeatModePresentation = when (mode) {
    Player.REPEAT_MODE_ALL -> RepeatModePresentation("Repeat all", showsOneIndicator = false)
    Player.REPEAT_MODE_ONE -> RepeatModePresentation("Repeat one", showsOneIndicator = true)
    else -> RepeatModePresentation("Repeat off", showsOneIndicator = false)
}

internal fun nextRepeatMode(mode: Int): Int = when (mode) {
    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
    else -> Player.REPEAT_MODE_OFF
}
