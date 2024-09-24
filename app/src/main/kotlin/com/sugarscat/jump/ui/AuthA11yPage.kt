package com.sugarscat.jump.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.StringUtils.getString
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.sugarscat.jump.MainActivity
import com.sugarscat.jump.R
import com.sugarscat.jump.permission.shizukuOkState
import com.sugarscat.jump.permission.writeSecureSettingsState
import com.sugarscat.jump.service.JumpAbService
import com.sugarscat.jump.service.fixRestartService
import com.sugarscat.jump.shizuku.newPackageManager
import com.sugarscat.jump.ui.component.updateDialogOptions
import com.sugarscat.jump.ui.style.itemHorizontalPadding
import com.sugarscat.jump.util.LocalNavController
import com.sugarscat.jump.util.ProfileTransitions
import com.sugarscat.jump.util.launchAsFn
import com.sugarscat.jump.util.openA11ySettings
import com.sugarscat.jump.util.openUri
import com.sugarscat.jump.util.throttle
import com.sugarscat.jump.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import rikka.shizuku.Shizuku
import java.io.DataOutputStream

@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun AuthA11yPage() {
    val context = LocalContext.current as MainActivity
    val navController = LocalNavController.current

    val vm = viewModel<AuthA11yVm>()
    val showCopyDlg by vm.showCopyDlgFlow.collectAsState()
    val writeSecureSettings by writeSecureSettingsState.stateFlow.collectAsState()
    val a11yRunning by JumpAbService.isRunning.collectAsState()

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
        TopAppBar(scrollBehavior = scrollBehavior, navigationIcon = {
            IconButton(onClick = {
                navController.popBackStack()
            }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                )
            }
        }, title = {
            Text(text = getString(R.string.authorization_status))
        }, actions = {})
    }) { contentPadding ->
        Column(
            modifier = Modifier.padding(contentPadding)
        ) {
            Text(
                text = getString(R.string.authorization_status_tip),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(itemHorizontalPadding)
            )
            Card(
                modifier = Modifier
                    .padding(itemHorizontalPadding, 0.dp)
                    .fillMaxWidth(),
                onClick = { }
            ) {
                Text(
                    modifier = Modifier.padding(cardHorizontalPadding, 8.dp),
                    text = getString(R.string.general_authorization),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    modifier = Modifier.padding(cardHorizontalPadding, 0.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    text = getString(R.string.general_authorization_desc)
                )
                if (writeSecureSettings || a11yRunning) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        modifier = Modifier.padding(cardHorizontalPadding, 0.dp),
                        text = getString(R.string.already_have_accessibility_permission),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                } else {
                    Row(
                        modifier = Modifier
                            .padding(4.dp, 0.dp)
                            .fillMaxWidth(),
                    ) {
                        TextButton(onClick = throttle { openA11ySettings() }) {
                            Text(
                                text = getString(R.string.manual_authorization),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                    Text(
                        modifier = Modifier
                            .padding(cardHorizontalPadding, 0.dp)
                            .clickable {
                                context.openUri("https://gkd.li/?r=2")
                            },
                        text = getString(R.string.unable_to_turn_on_accessibility),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier
                    .padding(itemHorizontalPadding, 0.dp)
                    .fillMaxWidth(),
                onClick = { }
            ) {
                Text(
                    modifier = Modifier.padding(cardHorizontalPadding, 8.dp),
                    text = getString(R.string.advanced_authorization),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    modifier = Modifier.padding(cardHorizontalPadding, 0.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    text = getString(R.string.advanced_authorization_desc)
                )
                if (!writeSecureSettings) {
                    Row(
                        modifier = Modifier
                            .padding(4.dp, 0.dp)
                            .fillMaxWidth(),
                    ) {
                        TextButton(onClick = throttle(fn = vm.viewModelScope.launchAsFn(Dispatchers.IO) {
                            context.grantPermissionByShizuku()
                        })) {
                            Text(
                                text = getString(R.string.shizuku_authorization),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        TextButton(onClick = throttle(fn = vm.viewModelScope.launchAsFn(Dispatchers.IO) {
                            grantPermissionByRoot()
                        })) {
                            Text(
                                text = getString(R.string.root_authorization),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        TextButton(onClick = {
                            vm.showCopyDlgFlow.value = true
                        }) {
                            Text(
                                text = getString(R.string.manual_authorization),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        modifier = Modifier.padding(cardHorizontalPadding, 0.dp),
                        text = getString(R.string.prefer_write_security_settings),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(
                        modifier = Modifier
                            .padding(4.dp, 0.dp)
                            .fillMaxWidth(),
                    ) {
                        TextButton(onClick = throttle {
                            context.mainVm.dialogFlow.updateDialogOptions(
                                title = getString(R.string.keep_alive_without_feeling),
                                text = getString(
                                    R.string.keep_alive_without_feeling_desc,
                                    com.sugarscat.jump.META.appName
                                )
                            )
                        }) {
                            Text(
                                text = getString(R.string.keep_alive_without_feeling),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }

    if (showCopyDlg) {
        AlertDialog(
            onDismissRequest = { vm.showCopyDlgFlow.value = false },
            title = { Text(text = getString(R.string.manual_authorization)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(text = getString(R.string.adb_authorization_desc))
                    Spacer(modifier = Modifier.height(4.dp))
                    SelectionContainer {
                        Text(
                            text = commandText,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.showCopyDlgFlow.value = false
                    ClipboardUtils.copyText(commandText)
                    toast(getString(R.string.copied))
                }) {
                    Text(text = getString(R.string.copy_and_close))
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.showCopyDlgFlow.value = false }) {
                    Text(text = getString(R.string.close))
                }
            }
        )
    }
}

private val commandText by lazy { "adb pm grant ${com.sugarscat.jump.META.appId} android.permission.WRITE_SECURE_SETTINGS" }

private suspend fun MainActivity.grantPermissionByShizuku() {
    if (shizukuOkState.stateFlow.value) {
        try {
            val service = newPackageManager()
            if (service != null) {
                service.grantRuntimePermission(
                    com.sugarscat.jump.META.appId,
                    "android.permission.WRITE_SECURE_SETTINGS",
                    0, // maybe others
                )
                delay(500)
                if (writeSecureSettingsState.updateAndGet()) {
                    toast(getString(R.string.authorization_success))
                    fixRestartService()
                }
            }
        } catch (e: Exception) {
            toast(getString(R.string.authorization_failed_tip, e.message))
            LogUtils.d(e)
        }
    } else {
        try {
            Shizuku.requestPermission(Activity.RESULT_OK)
        } catch (e: Exception) {
            LogUtils.d("Shizuku 授权错误", e.message)
            mainVm.shizukuErrorFlow.value = true
        }
    }
}

private val cardHorizontalPadding = 12.dp

private fun grantPermissionByRoot() {
    var p: Process? = null
    try {
        p = Runtime.getRuntime().exec("su")
        val o = DataOutputStream(p.outputStream)
        o.writeBytes("pm grant ${com.sugarscat.jump.META.appId} android.permission.WRITE_SECURE_SETTINGS\nexit\n")
        o.flush()
        o.close()
        p.waitFor()
        if (p.exitValue() == 0) {
            toast(getString(R.string.authorization_success))
        }
    } catch (e: Exception) {
        toast(getString(R.string.authorization_failed_tip, e.message))
        LogUtils.d(e)
    } finally {
        p?.destroy()
    }
}
