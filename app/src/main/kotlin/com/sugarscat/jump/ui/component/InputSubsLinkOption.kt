package com.sugarscat.jump.ui.component

import android.webkit.URLUtil
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.StringUtils.getString
import com.sugarscat.jump.MainActivity
import com.sugarscat.jump.MainViewModel
import com.sugarscat.jump.R
import com.sugarscat.jump.util.isSafeUrl
import com.sugarscat.jump.util.launchAsFn
import com.sugarscat.jump.util.subsItemsFlow
import com.sugarscat.jump.util.throttle
import com.sugarscat.jump.util.toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


class InputSubsLinkOption {
    private val showFlow = MutableStateFlow(false)
    private val valueFlow = MutableStateFlow("")
    private val initValueFlow = MutableStateFlow("")
    private var continuation: Continuation<String?>? = null

    private fun resume(value: String?) {
        showFlow.value = false
        valueFlow.value = ""
        initValueFlow.value = ""
        continuation?.resume(value)
        continuation = null
    }

    private suspend fun submit(mainVm: MainViewModel) {
        val value = valueFlow.value
        if (!URLUtil.isNetworkUrl(value)) {
            toast(getString(R.string.illegal_link))
            return
        }
        val initValue = initValueFlow.value
        if (initValue.isNotEmpty() && initValue == value) {
            toast(getString(R.string.unmodified))
            resume(null)
            return
        }
        if (subsItemsFlow.value.any { it.updateUrl == value }) {
            toast(getString(R.string.same_link))
            return
        }
        if (!isSafeUrl(value)) {
            mainVm.dialogFlow.waitResult(
                title = getString(R.string.unknown_source),
                text = getString(R.string.unknown_source_tip)
            )
        }
        resume(value)
    }

    private fun cancel() = resume(null)

    suspend fun getResult(initValue: String = ""): String? {
        initValueFlow.value = initValue
        valueFlow.value = initValue
        showFlow.value = true
        return suspendCoroutine {
            continuation = it
        }
    }

    @Composable
    fun ContentDialog() {
        val show by showFlow.collectAsState()
        if (show) {
            val context = LocalContext.current as MainActivity
            val value by valueFlow.collectAsState()
            val initValue by initValueFlow.collectAsState()
            AlertDialog(
                title = {
                    Text(
                        text =
                        if (initValue.isNotEmpty())
                            getString(R.string.modify_subscription)
                        else
                            getString(R.string.add_subscription)
                    )
                },
                text = {
                    OutlinedTextField(
                        value = value,
                        onValueChange = {
                            valueFlow.value = it.trim()
                        },
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(text = getString(R.string.input_subscription_link))
                        },
                        isError = value.isNotEmpty() && !URLUtil.isNetworkUrl(value),
                    )
                },
                onDismissRequest = {
                    if (valueFlow.value.isEmpty()) {
                        cancel()
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = value.isNotEmpty(),
                        onClick = throttle(fn = context.mainVm.viewModelScope.launchAsFn {
                            submit(context.mainVm)
                        }),
                    ) {
                        Text(text = getString(R.string.confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = ::cancel) {
                        Text(text = getString(R.string.cancel))
                    }
                },
            )
        }
    }
}

