package com.sugarscat.jump.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.blankj.utilcode.util.StringUtils.getString
import com.sugarscat.jump.R
import com.sugarscat.jump.util.throttle
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.yield
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class AlertDialogOptions(
    val text: @Composable (() -> Unit)? = null,
    val title: @Composable (() -> Unit)? = null,
    val onDismissRequest: (() -> Unit)? = null,
    val confirmButton: @Composable () -> Unit,
    val dismissButton: @Composable (() -> Unit)? = null,
)

private fun buildDialogOptions(
    title: String,
    text: String,
    confirmText: String,
    confirmAction: () -> Unit,
    dismissText: String? = null,
    dismissAction: (() -> Unit)? = null,
    onDismissRequest: (() -> Unit)? = null,
    error: Boolean = false,
): AlertDialogOptions {
    return AlertDialogOptions(
        text = {
            Text(text = text)
        },
        title = {
            Text(text = title)
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = throttle(fn = confirmAction),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (error) MaterialTheme.colorScheme.error else Color.Unspecified
                )
            ) {
                Text(text = confirmText)
            }
        },
        dismissButton = if (dismissText != null && dismissAction != null) {
            {
                TextButton(
                    onClick = throttle(fn = dismissAction),
                ) {
                    Text(text = dismissText)
                }
            }
        } else {
            null
        },
    )
}

@Composable
fun BuildDialog(stateFlow: MutableStateFlow<AlertDialogOptions?>) {
    val options by stateFlow.collectAsState()
    options?.let {
        AlertDialog(
            text = it.text,
            title = it.title,
            onDismissRequest = it.onDismissRequest ?: { stateFlow.value = null },
            confirmButton = it.confirmButton,
            dismissButton = it.dismissButton,
        )
    }
}

fun MutableStateFlow<AlertDialogOptions?>.updateDialogOptions(
    title: String,
    text: String,
    confirmText: String = getString(R.string.i_see),
    confirmAction: (() -> Unit)? = null,
    dismissText: String? = null,
    dismissAction: (() -> Unit)? = null,
    onDismissRequest: (() -> Unit)? = null,
    error: Boolean = false,
) {
    value = buildDialogOptions(
        title = title,
        text = text,
        confirmText = confirmText,
        confirmAction = confirmAction ?: { value = null },
        dismissText = dismissText,
        dismissAction = dismissAction,
        onDismissRequest = onDismissRequest,
        error = error,
    )
}

val defaultConfirmText: String = getString(R.string.confirm)
val defaultDismissText: String = getString(R.string.cancel)

private suspend fun MutableStateFlow<AlertDialogOptions?>.getResult(
    title: String,
    text: String,
    confirmText: String = defaultConfirmText,
    dismissText: String = defaultDismissText,
    error: Boolean = false,
): Boolean {
    return suspendCoroutine { s ->
        this.value = buildDialogOptions(
            title = title,
            text = text,
            onDismissRequest = {},
            confirmText = confirmText,
            confirmAction = {
                s.resume(true)
                this.value = null
            },
            dismissText = dismissText,
            dismissAction = {
                s.resume(false)
                this.value = null
            },
            error = error,
        )
    }
}

suspend fun MutableStateFlow<AlertDialogOptions?>.waitResult(
    title: String,
    text: String,
    confirmText: String = defaultConfirmText,
    dismissText: String = defaultDismissText,
    error: Boolean = false,
) {
    val r = getResult(
        title = title,
        text = text,
        confirmText = confirmText,
        dismissText = dismissText,
        error = error,
    )
    if (!r) {
        coroutineContext[Job]?.cancel()
        yield()
    }
}