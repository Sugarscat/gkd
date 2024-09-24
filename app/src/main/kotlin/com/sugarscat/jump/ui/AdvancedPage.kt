package com.sugarscat.jump.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.StringUtils.getString
import com.dylanc.activityresult.launcher.launchForResult
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.ActivityLogPageDestination
import com.ramcosta.composedestinations.generated.destinations.SnapshotPageDestination
import com.ramcosta.composedestinations.utils.toDestinationsNavigator
import com.sugarscat.jump.MainActivity
import com.sugarscat.jump.R
import com.sugarscat.jump.debug.FloatingService
import com.sugarscat.jump.debug.HttpService
import com.sugarscat.jump.debug.ScreenshotService
import com.sugarscat.jump.permission.canDrawOverlaysState
import com.sugarscat.jump.permission.notificationState
import com.sugarscat.jump.permission.requiredPermission
import com.sugarscat.jump.permission.shizukuOkState
import com.sugarscat.jump.shizuku.CommandResult
import com.sugarscat.jump.shizuku.newActivityTaskManager
import com.sugarscat.jump.shizuku.newUserService
import com.sugarscat.jump.shizuku.safeGetTasks
import com.sugarscat.jump.ui.component.AuthCard
import com.sugarscat.jump.ui.component.SettingItem
import com.sugarscat.jump.ui.component.TextSwitch
import com.sugarscat.jump.ui.component.updateDialogOptions
import com.sugarscat.jump.ui.style.EmptyHeight
import com.sugarscat.jump.ui.style.itemPadding
import com.sugarscat.jump.ui.style.titleItemPadding
import com.sugarscat.jump.util.LocalNavController
import com.sugarscat.jump.util.ProfileTransitions
import com.sugarscat.jump.util.buildLogFile
import com.sugarscat.jump.util.json
import com.sugarscat.jump.util.launchAsFn
import com.sugarscat.jump.util.launchTry
import com.sugarscat.jump.util.openUri
import com.sugarscat.jump.util.privacyStoreFlow
import com.sugarscat.jump.util.saveFileToDownloads
import com.sugarscat.jump.util.shareFile
import com.sugarscat.jump.util.storeFlow
import com.sugarscat.jump.util.throttle
import com.sugarscat.jump.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import rikka.shizuku.Shizuku

@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun AdvancedPage() {
    val context = LocalContext.current as MainActivity
    val vm = viewModel<AdvancedVm>()
    val navController = LocalNavController.current
    val store by storeFlow.collectAsState()
    val snapshotCount by vm.snapshotCountFlow.collectAsState()

    vm.uploadOptions.ShowDialog()

    var showEditPortDlg by remember {
        mutableStateOf(false)
    }
    if (showEditPortDlg) {
        var value by remember {
            mutableStateOf(store.httpServerPort.toString())
        }
        AlertDialog(title = { Text(text = getString(R.string.service_port)) }, text = {
            OutlinedTextField(
                value = value,
                placeholder = {
                    Text(text = getString(R.string.enter_an_integer))
                },
                onValueChange = {
                    value = it.filter { c -> c.isDigit() }.take(5)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = {
                    Text(
                        text = "${value.length} / 5",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
                    )
                },
            )
        }, onDismissRequest = {
            if (value.isEmpty()) {
                showEditPortDlg = false
            }
        }, confirmButton = {
            TextButton(
                enabled = value.isNotEmpty(),
                onClick = {
                    val newPort = value.toIntOrNull()
                    if (newPort == null || !(5000 <= newPort && newPort <= 65535)) {
                        toast(getString(R.string.enter_an_integer))
                        return@TextButton
                    }
                    storeFlow.value = store.copy(
                        httpServerPort = newPort
                    )
                    showEditPortDlg = false
                }
            ) {
                Text(
                    text = getString(R.string.confirm), modifier = Modifier
                )
            }
        }, dismissButton = {
            TextButton(onClick = { showEditPortDlg = false }) {
                Text(
                    text = getString(R.string.cancel)
                )
            }
        })
    }

    var showShareLogDlg by remember {
        mutableStateOf(false)
    }
    if (showShareLogDlg) {
        Dialog(onDismissRequest = { showShareLogDlg = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                val modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                Text(
                    text = getString(R.string.share_to_other_apps), modifier = Modifier
                        .clickable(onClick = throttle {
                            showShareLogDlg = false
                            vm.viewModelScope.launchTry(Dispatchers.IO) {
                                val logZipFile = buildLogFile()
                                context.shareFile(logZipFile, getString(R.string.share_log_files))
                            }
                        })
                        .then(modifier)
                )
                Text(
                    text = getString(R.string.save_to_downloads), modifier = Modifier
                        .clickable(onClick = throttle {
                            showShareLogDlg = false
                            vm.viewModelScope.launchTry(Dispatchers.IO) {
                                val logZipFile = buildLogFile()
                                context.saveFileToDownloads(logZipFile)
                            }
                        })
                        .then(modifier)
                )
                Text(
                    text = getString(R.string.generate_link),
                    modifier = Modifier
                        .clickable(onClick = throttle {
                            showShareLogDlg = false
                            vm.uploadOptions.startTask(getFile = { buildLogFile() })
                        })
                        .then(modifier)
                )
            }
        }
    }

    var showEditCookieDlg by remember { mutableStateOf(false) }
    if (showEditCookieDlg) {
        val privacyStore by privacyStoreFlow.collectAsState()
        var value by remember {
            mutableStateOf(privacyStore.githubCookie ?: "")
        }
        AlertDialog(
            onDismissRequest = {
                if (value.isEmpty()) {
                    showEditCookieDlg = false
                }
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = getString(R.string.github_cookie))
                    IconButton(onClick = throttle {
                        context.openUri(HELP_URL)
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                            contentDescription = null,
                        )
                    }
                }
            },
            text = {
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = it.filter { c -> c != '\n' && c != '\r' }
                    },
                    placeholder = { Text(text = getString(R.string.enter_github_cookie)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 10,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showEditCookieDlg = false
                    privacyStoreFlow.update { it.copy(githubCookie = value.trim()) }
                }) {
                    Text(text = getString(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditCookieDlg = false }) {
                    Text(text = getString(R.string.cancel))
                }
            }
        )
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(scrollBehavior = scrollBehavior, navigationIcon = {
                IconButton(onClick = {
                    navController.popBackStack()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                    )
                }
            }, title = { Text(text = getString(R.string.advanced_settings)) }, actions = {})
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding),
        ) {
            Text(
                text = getString(R.string.shizuku),
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            val shizukuOk by shizukuOkState.stateFlow.collectAsState()
            if (!shizukuOk) {
                AuthCard(title = getString(R.string.shizuku_authorization),
                    desc = getString(R.string.shizuku_authorization_desc),
                    onAuthClick = {
                        try {
                            Shizuku.requestPermission(Activity.RESULT_OK)
                        } catch (e: Exception) {
                            LogUtils.d(getString(R.string.shizuku_authorization_failed), e.message)
                            context.mainVm.shizukuErrorFlow.value = true
                        }
                    })
                ShizukuFragment(false)
            } else {
                ShizukuFragment()
            }

            val httpServerRunning by HttpService.isRunning.collectAsState()
            val localNetworkIps by HttpService.localNetworkIpsFlow.collectAsState()

            Text(
                text = getString(R.string.http_service),
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(
                modifier = Modifier.itemPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = getString(R.string.http_service),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    CompositionLocalProvider(
                        LocalTextStyle provides MaterialTheme.typography.bodyMedium
                    ) {
                        if (!httpServerRunning) {
                            Text(
                                text = getString(R.string.browser_connection),
                            )
                        } else {
                            Text(
                                text = getString(R.string.click_links_connect_automatically),
                            )
                            Row {
                                Text(
                                    text = "http://127.0.0.1:${store.httpServerPort}",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = LocalTextStyle.current.copy(textDecoration = TextDecoration.Underline),
                                    modifier = Modifier.clickable(onClick = throttle {
                                        context.openUri("http://127.0.0.1:${store.httpServerPort}")
                                    }),
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(text = getString(R.string.accessible_only_by_this_device))
                            }
                            localNetworkIps.forEach { host ->
                                Text(
                                    text = "http://${host}:${store.httpServerPort}",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = LocalTextStyle.current.copy(textDecoration = TextDecoration.Underline),
                                    modifier = Modifier.clickable(onClick = throttle {
                                        context.openUri("http://${host}:${store.httpServerPort}")
                                    })
                                )
                            }
                        }
                    }
                }
                Switch(
                    checked = httpServerRunning,
                    onCheckedChange = vm.viewModelScope.launchAsFn<Boolean> {
                        if (it) {
                            requiredPermission(context, notificationState)
                            HttpService.start()
                        } else {
                            HttpService.stop()
                        }
                    }
                )
            }

            SettingItem(
                title = getString(R.string.service_port),
                subtitle = store.httpServerPort.toString(),
                imageVector = Icons.Default.Edit,
                onClick = {
                    showEditPortDlg = true
                }
            )

            TextSwitch(
                title = getString(R.string.clear_subscription),
                subtitle = getString(R.string.auto_delete_memory_subscription),
                checked = store.autoClearMemorySubs
            ) {
                storeFlow.value = store.copy(
                    autoClearMemorySubs = it
                )
            }

            Text(
                text = getString(R.string.snapshot),
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            SettingItem(
                title = getString(R.string.snapshot_record) + (if (snapshotCount > 0) "-$snapshotCount" else ""),
                onClick = {
                    navController.toDestinationsNavigator().navigate(SnapshotPageDestination)
                }
            )

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                val screenshotRunning by ScreenshotService.isRunning.collectAsState()
                TextSwitch(
                    title = getString(R.string.screenshot_service),
                    subtitle = getString(R.string.screenshot_service_desc),
                    checked = screenshotRunning,
                    onCheckedChange = vm.viewModelScope.launchAsFn<Boolean> {
                        if (it) {
                            requiredPermission(context, notificationState)
                            val mediaProjectionManager =
                                context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                            val activityResult =
                                context.launcher.launchForResult(mediaProjectionManager.createScreenCaptureIntent())
                            if (activityResult.resultCode == Activity.RESULT_OK && activityResult.data != null) {
                                ScreenshotService.start(intent = activityResult.data!!)
                            }
                        } else {
                            ScreenshotService.stop()
                        }
                    }
                )
            }

            val floatingRunning by FloatingService.isRunning.collectAsState()
            TextSwitch(
                title = getString(R.string.floating_service),
                subtitle = getString(R.string.floating_service_desc),
                checked = floatingRunning,
                onCheckedChange = vm.viewModelScope.launchAsFn<Boolean> {
                    if (it) {
                        requiredPermission(context, notificationState)
                        requiredPermission(context, canDrawOverlaysState)
                        val intent = Intent(context, FloatingService::class.java)
                        ContextCompat.startForegroundService(context, intent)
                    } else {
                        FloatingService.stop(context)
                    }
                }
            )

            TextSwitch(
                title = getString(R.string.volume_snapshot),
                subtitle = getString(R.string.volume_snapshot_desc),
                checked = store.captureVolumeChange
            ) {
                storeFlow.value = store.copy(
                    captureVolumeChange = it
                )
            }

            TextSwitch(
                title = getString(R.string.capture_screenshot),
                subtitle = getString(R.string.capture_screenshot_desc),
                suffix = getString(R.string.view_restrictions),
                onSuffixClick = {
                    context.mainVm.dialogFlow.updateDialogOptions(
                        title = getString(R.string.restrictions),
                        text = getString(R.string.capture_screenshot_tip),
                    )
                },
                checked = store.captureScreenshot
            ) {
                storeFlow.value = store.copy(
                    captureScreenshot = it
                )
            }

            TextSwitch(
                title = getString(R.string.hide_status_bar),
                subtitle = getString(R.string.hide_status_bar_desc),
                checked = store.hideSnapshotStatusBar
            ) {
                storeFlow.value = store.copy(
                    hideSnapshotStatusBar = it
                )
            }

            TextSwitch(
                title = getString(R.string.save_tips),
                subtitle = getString(R.string.save_tips_desc),
                checked = store.showSaveSnapshotToast
            ) {
                storeFlow.value = store.copy(
                    showSaveSnapshotToast = it
                )
            }

            SettingItem(
                title = getString(R.string.github_cookie),
                subtitle = getString(R.string.github_cookie_desc),
                suffix = getString(R.string.view_tutorial),
                onSuffixClick = {
                    context.openUri(HELP_URL)
                },
                imageVector = Icons.Default.Edit,
                onClick = {
                    showEditCookieDlg = true
                }
            )

            Text(
                text = getString(R.string.interface_record),
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            TextSwitch(
                title = getString(R.string.record_interface),
                subtitle = getString(R.string.record_interface_desc),
                checked = store.enableActivityLog
            ) {
                storeFlow.value = store.copy(
                    enableActivityLog = it
                )
            }
            SettingItem(
                title = getString(R.string.interface_record),
                onClick = {
                    navController.toDestinationsNavigator().navigate(ActivityLogPageDestination)
                }
            )

            Text(
                text = getString(R.string.log),
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            TextSwitch(
                title = getString(R.string.save_log),
                subtitle = getString(R.string.save_log_desc),
                checked = store.log2FileSwitch,
                onCheckedChange = {
                    storeFlow.value = store.copy(
                        log2FileSwitch = it
                    )
                    if (!it) {
                        context.mainVm.viewModelScope.launchTry(Dispatchers.IO) {
                            val logFiles = LogUtils.getLogFiles()
                            if (logFiles.isNotEmpty()) {
                                logFiles.forEach { f ->
                                    f.delete()
                                }
                                toast(getString(R.string.all_logs_deleted))
                            }
                        }
                    }
                })

            if (store.log2FileSwitch) {
                SettingItem(
                    title = getString(R.string.export_log),
                    imageVector = Icons.Default.Share,
                    onClick = {
                        showShareLogDlg = true
                    }
                )
            }

            Text(
                text = getString(R.string.other),
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            TextSwitch(title = getString(R.string.enable_floating_window),
                subtitle = getString(R.string.enable_floating_window_desc),
                checked = store.enableAbFloatWindow,
                onCheckedChange = {
                    storeFlow.value = store.copy(
                        enableAbFloatWindow = it
                    )
                })

            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }

}

@Composable
private fun ShizukuFragment(enabled: Boolean = true) {
    val store by storeFlow.collectAsState()
    TextSwitch(title = getString(R.string.shizuku_interface_recognition),
        subtitle = getString(R.string.shizuku_interface_recognition_desc),
        checked = store.enableShizukuActivity,
        enabled = enabled,
        onCheckedChange = { enableShizuku ->
            if (enableShizuku) {
                com.sugarscat.jump.appScope.launchTry(Dispatchers.IO) {
                    // 校验方法是否适配, 再允许使用 shizuku
                    val tasks =
                        newActivityTaskManager()?.safeGetTasks()?.firstOrNull()
                    if (tasks != null) {
                        storeFlow.value = store.copy(
                            enableShizukuActivity = true
                        )
                    } else {
                        toast(getString(R.string.shizuku_verification_failed))
                    }
                }
            } else {
                storeFlow.value = store.copy(
                    enableShizukuActivity = false
                )
            }
        })

    TextSwitch(
        title = getString(R.string.shizuku_simulates_clicking),
        subtitle = getString(R.string.shizuku_simulates_clicking_desc),
        checked = store.enableShizukuClick,
        enabled = enabled,
        onCheckedChange = { enableShizuku ->
            if (enableShizuku) {
                com.sugarscat.jump.appScope.launchTry(Dispatchers.IO) {
                    val service = newUserService()
                    val result = service.userService.execCommand("input tap 0 0")
                    service.destroy()
                    if (json.decodeFromString<CommandResult>(result).code == 0) {
                        storeFlow.update { it.copy(enableShizukuClick = true) }
                    } else {
                        toast(getString(R.string.shizuku_verification_failed))
                    }
                }
            } else {
                storeFlow.value = store.copy(
                    enableShizukuClick = false
                )
            }

        })

}
