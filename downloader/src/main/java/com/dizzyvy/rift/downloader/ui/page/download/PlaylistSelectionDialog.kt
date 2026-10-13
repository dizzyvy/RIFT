package com.dizzyvy.rift.downloader.ui.page.download

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.text.isDigitsOnly
import com.dizzyvy.rift.downloader.R
import com.dizzyvy.rift.downloader.ui.component.ConfirmButton
import com.dizzyvy.rift.downloader.ui.component.DismissButton
import com.dizzyvy.rift.downloader.util.PlaylistResult
import com.dizzyvy.rift.downloader.util.ToastUtil
import com.dizzyvy.rift.downloader.util.isNumberInRange

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlaylistSelectionDialog(
    playlistInfo: PlaylistResult,
    onDismissRequest: () -> Unit = {},
    onConfirm: (IntRange) -> Unit = {},
) {
    val playlistCount = playlistInfo.entries?.size ?: 0
    var from by remember { mutableStateOf("1") }
    var to by remember { mutableStateOf(playlistCount.toString()) }
    var error by remember { mutableStateOf(false) }
    val (fromFocus, toFocus) = remember { FocusRequester.createRefs() }
    val onDone: () -> Unit = {
        error =
            !from.isNumberInRange(1, playlistCount) ||
                !to.isNumberInRange(1, playlistCount) ||
                from.toInt() > to.toInt()
        if (error) {
            ToastUtil.makeToast(R.string.invalid_index_range)
        } else {
            onConfirm(from.toInt()..to.toInt())
            onDismissRequest()
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = { Icon(Icons.Outlined.PlaylistAdd, contentDescription = null) },
        title = { Text(stringResource(R.string.download_range_selection)) },
        text = {
            Column {
                Text(
                    text =
                        stringResource(R.string.download_range_desc)
                            .format(1, playlistCount, playlistInfo.title)
                )
                Row(modifier = Modifier.padding(top = 12.dp)) {
                    Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                        OutlinedTextField(
                            modifier =
                                Modifier.focusable()
                                    .focusProperties { next = toFocus }
                                    .focusRequester(fromFocus),
                            value = from,
                            onValueChange = {
                                if (it.isDigitsOnly()) from = it
                                error = false
                            },
                            label = { Text(stringResource(R.string.from)) },
                            keyboardOptions =
                                KeyboardOptions(
                                    keyboardType = KeyboardType.NumberPassword,
                                    imeAction = ImeAction.Next,
                                ),
                            singleLine = true,
                            isError = error,
                        )
                    }
                    Column(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                        OutlinedTextField(
                            modifier =
                                Modifier.focusable()
                                    .focusProperties { previous = fromFocus }
                                    .focusRequester(toFocus),
                            value = to,
                            onValueChange = {
                                if (it.isDigitsOnly()) to = it
                                error = false
                            },
                            label = { Text(stringResource(R.string.to)) },
                            keyboardOptions =
                                KeyboardOptions(
                                    keyboardType = KeyboardType.NumberPassword,
                                    imeAction = ImeAction.Done,
                                ),
                            keyboardActions = KeyboardActions(onDone = { onDone() }),
                            singleLine = true,
                            isError = error,
                        )
                    }
                }
            }
        },
        dismissButton = { DismissButton { onDismissRequest() } },
        confirmButton = { ConfirmButton(onClick = onDone) },
    )
}
