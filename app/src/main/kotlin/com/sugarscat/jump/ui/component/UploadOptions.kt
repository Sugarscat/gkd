package com.sugarscat.jump.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.StringUtils.getString
import com.sugarscat.jump.R
import com.sugarscat.jump.data.GithubPoliciesAsset
import com.sugarscat.jump.util.LoadStatus
import com.sugarscat.jump.util.launchTry
import com.sugarscat.jump.util.privacyStoreFlow
import com.sugarscat.jump.util.toast
import com.sugarscat.jump.util.uploadFileToGithub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

class UploadOptions(
    private val scope: CoroutineScope,
    private val showHref: (GithubPoliciesAsset) -> String = { it.shortHref }
) {
    private val statusFlow = MutableStateFlow<LoadStatus<GithubPoliciesAsset>?>(null)
    private var job: Job? = null
    private fun buildTask(
        cookie: String,
        getFile: suspend () -> File,
        onSuccessResult: ((GithubPoliciesAsset) -> Unit)?
    ) = scope.launchTry(Dispatchers.IO) {
        statusFlow.value = LoadStatus.Loading()
        try {
            val policiesAsset = uploadFileToGithub(cookie, getFile()) {
                if (statusFlow.value is LoadStatus.Loading) {
                    statusFlow.value = LoadStatus.Loading(it)
                }
            }
            statusFlow.value = LoadStatus.Success(policiesAsset)
            onSuccessResult?.invoke(policiesAsset)
        } catch (e: Exception) {
            statusFlow.value = LoadStatus.Failure(e)
        } finally {
            job = null
        }
    }

    fun startTask(
        getFile: suspend () -> File,
        onSuccessResult: ((GithubPoliciesAsset) -> Unit)? = null
    ) {
        val cookie = privacyStoreFlow.value.githubCookie
        if (cookie.isNullOrBlank()) {
            toast(getString(R.string.please_set_cookies))
            return
        }
        if (job != null || statusFlow.value is LoadStatus.Loading) {
            return
        }
        job = buildTask(cookie, getFile, onSuccessResult)
    }

    private fun stopTask() {
        if (statusFlow.value is LoadStatus.Loading && job != null) {
            job?.cancel(getString(R.string.upload_canceled))
            job = null
        }
    }


    @Composable
    fun ShowDialog() {
        when (val status = statusFlow.collectAsState().value) {
            null -> {}
            is LoadStatus.Loading -> {
                AlertDialog(
                    title = { Text(text = getString(R.string.uploading_files)) },
                    text = {
                        LinearProgressIndicator(
                            progress = { status.progress },
                        )
                    },
                    onDismissRequest = { },
                    confirmButton = {
                        TextButton(onClick = {
                            stopTask()
                        }) {
                            Text(text = getString(R.string.terminate_upload))
                        }
                    },
                )
            }

            is LoadStatus.Success -> {
                val href = showHref(status.result)
                AlertDialog(title = { Text(text = getString(R.string.upload_completed)) }, text = {
                    Text(text = href)
                }, onDismissRequest = {}, dismissButton = {
                    TextButton(onClick = {
                        statusFlow.value = null
                    }) {
                        Text(text = getString(R.string.close))
                    }
                }, confirmButton = {
                    TextButton(onClick = {
                        ClipboardUtils.copyText(href)
                        toast(getString(R.string.copied))
                        statusFlow.value = null
                    }) {
                        Text(text = getString(R.string.copy_and_close))
                    }
                })
            }

            is LoadStatus.Failure -> {
                AlertDialog(
                    title = { Text(text = getString(R.string.upload_failed)) },
                    text = {
                        Text(text = status.exception.let {
                            it.message ?: it.toString()
                        })
                    },
                    onDismissRequest = { statusFlow.value = null },
                    confirmButton = {
                        TextButton(onClick = {
                            statusFlow.value = null
                        }) {
                            Text(text = getString(R.string.close))
                        }
                    },
                )
            }
        }
    }
}
