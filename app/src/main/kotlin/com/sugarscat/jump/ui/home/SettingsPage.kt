package com.sugarscat.jump.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blankj.utilcode.util.StringUtils.getString
import com.ramcosta.composedestinations.generated.destinations.AboutPageDestination
import com.ramcosta.composedestinations.generated.destinations.AdvancedPageDestination
import com.ramcosta.composedestinations.utils.toDestinationsNavigator
import com.sugarscat.jump.META
import com.sugarscat.jump.MainActivity
import com.sugarscat.jump.R
import com.sugarscat.jump.ui.component.RotatingLoadingIcon
import com.sugarscat.jump.ui.component.SettingItem
import com.sugarscat.jump.ui.component.TextMenu
import com.sugarscat.jump.ui.component.TextSwitch
import com.sugarscat.jump.ui.component.updateDialogOptions
import com.sugarscat.jump.ui.component.waitResult
import com.sugarscat.jump.ui.style.EmptyHeight
import com.sugarscat.jump.ui.style.itemPadding
import com.sugarscat.jump.ui.style.titleItemPadding
import com.sugarscat.jump.ui.theme.supportDynamicColor
import com.sugarscat.jump.util.DarkThemeOption
import com.sugarscat.jump.util.LocalNavController
import com.sugarscat.jump.util.UpdateChannelOption
import com.sugarscat.jump.util.checkUpdate
import com.sugarscat.jump.util.findOption
import com.sugarscat.jump.util.launchAsFn
import com.sugarscat.jump.util.launchTry
import com.sugarscat.jump.util.storeFlow
import com.sugarscat.jump.util.throttle
import com.sugarscat.jump.util.toast
import kotlinx.coroutines.flow.update

val settingsNav = BottomNavItem(
    label = getString(R.string.nav_settings), icon = Icons.Outlined.Settings
)

@Composable
fun useSettingsPage(): ScaffoldExt {
    val context = LocalContext.current as MainActivity
    val navController = LocalNavController.current
    val store by storeFlow.collectAsState()
    val vm = viewModel<HomeVm>()

    var showToastInputDlg by remember {
        mutableStateOf(false)
    }
    var showNotifTextInputDlg by remember {
        mutableStateOf(false)
    }

    val checkUpdating by context.mainVm.updateStatus.checkUpdatingFlow.collectAsState()

    if (showToastInputDlg) {
        var value by remember {
            mutableStateOf(store.clickToast)
        }
        val maxCharLen = 32
        AlertDialog(title = { Text(text = getString(R.string.trigger_tip)) }, text = {
            OutlinedTextField(
                value = value,
                placeholder = {
                    Text(text = getString(R.string.please_enter_content))
                },
                onValueChange = {
                    value = it.take(maxCharLen)
                },
                singleLine = true,
                supportingText = {
                    Text(
                        text = "${value.length} / $maxCharLen",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
                    )
                },
            )
        }, onDismissRequest = {
            if (value.isEmpty()) {
                showToastInputDlg = false
            }
        }, confirmButton = {
            TextButton(enabled = value.isNotEmpty(), onClick = {
                storeFlow.update { it.copy(clickToast = value) }
                showToastInputDlg = false
            }) {
                Text(
                    text = getString(R.string.confirm),
                )
            }
        }, dismissButton = {
            TextButton(onClick = { showToastInputDlg = false }) {
                Text(
                    text = getString(R.string.cancel),
                )
            }
        })
    }
    if (showNotifTextInputDlg) {
        var value by remember {
            mutableStateOf(store.customNotIfText)
        }
        val maxCharLen = 64
        AlertDialog(title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = getString(R.string.notification_content))
                IconButton(onClick = throttle {
                    context.mainVm.dialogFlow.updateDialogOptions(
                        title = getString(R.string.content_rules),
                        text = getString(R.string.content_rules_desc),
                    )
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                        contentDescription = null,
                    )
                }
            }
        }, text = {
            OutlinedTextField(
                value = value,
                placeholder = {
                    Text(text = getString(R.string.please_enter_content))
                },
                onValueChange = {
                    value = it.take(maxCharLen)
                },
                singleLine = true,
                supportingText = {
                    Text(
                        text = "${value.length} / $maxCharLen",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
                    )
                },
            )
        }, onDismissRequest = {
            if (value.isEmpty()) {
                showNotifTextInputDlg = false
            }
        }, confirmButton = {
            TextButton(enabled = value.isNotEmpty(), onClick = {
                storeFlow.update { it.copy(customNotIfText = value) }
                showNotifTextInputDlg = false
            }) {
                Text(
                    text = getString(R.string.confirm),
                )
            }
        }, dismissButton = {
            TextButton(onClick = { showNotifTextInputDlg = false }) {
                Text(
                    text = getString(R.string.cancel),
                )
            }
        })
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val scrollState = rememberScrollState()
    return ScaffoldExt(
        navItem = settingsNav,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(scrollBehavior = scrollBehavior, title = {
                Text(
                    text = settingsNav.label,
                )
            })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(padding)
        ) {

            Text(
                text = getString(R.string.common),
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            TextSwitch(
                title = getString(R.string.trigger_tip),
                subtitle = store.clickToast,
                checked = store.toastWhenClick,
                modifier = Modifier.clickable {
                    showToastInputDlg = true
                },
                onCheckedChange = {
                    storeFlow.value = store.copy(
                        toastWhenClick = it
                    )
                })

            if (store.toastWhenClick) {
                TextSwitch(
                    title = getString(R.string.system_tip),
                    subtitle = getString(R.string.system_tip_sub),
                    suffix = getString(R.string.view_restrictions),
                    onSuffixClick = {
                        context.mainVm.dialogFlow.updateDialogOptions(
                            title = getString(R.string.restrictions),
                            text = getString(R.string.restrictions_desc),
                        )
                    },
                    checked = store.useSystemToast,
                    onCheckedChange = {
                        storeFlow.value = store.copy(
                            useSystemToast = it
                        )
                    })
            }

            val subsStatus by vm.subsStatusFlow.collectAsState()
            TextSwitch(
                title = getString(R.string.notification_content),
                subtitle = if (store.useCustomNotIfText) store.customNotIfText else subsStatus,
                checked = store.useCustomNotIfText,
                modifier = Modifier.clickable {
                    showNotifTextInputDlg = true
                },
                onCheckedChange = {
                    storeFlow.value = store.copy(
                        useCustomNotIfText = it
                    )
                })

            TextSwitch(
                title = getString(R.string.hidden_in_the_background),
                subtitle = getString(R.string.hidden_in_the_background_sub),
                checked = store.excludeFromRecents,
                onCheckedChange = {
                    storeFlow.value = store.copy(
                        excludeFromRecents = it
                    )
                })

            Text(
                text = getString(R.string.theme),
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            TextMenu(
                title = getString(R.string.dark_mode),
                option = DarkThemeOption.allSubObject.findOption(store.enableDarkTheme)
            ) {
                storeFlow.update { s -> s.copy(enableDarkTheme = it.value) }
            }

            if (supportDynamicColor) {
                TextSwitch(title = getString(R.string.dynamic_color),
                    subtitle = getString(R.string.dynamic_color_sub),
                    checked = store.enableDynamicColor,
                    onCheckedChange = {
                        storeFlow.value = store.copy(
                            enableDynamicColor = it
                        )
                    })
            }

            if (META.updateEnabled) {
                Text(
                    text = getString(R.string.update),
                    modifier = Modifier.titleItemPadding(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )

                TextSwitch(
                    title = getString(R.string.auto_update),
                    subtitle = getString(R.string.auto_update_sub),
                    checked = store.autoCheckAppUpdate,
                    onCheckedChange = {
                        storeFlow.value = store.copy(
                            autoCheckAppUpdate = it
                        )
                    }
                )

                TextMenu(
                    title = getString(R.string.update_channel),
                    option = UpdateChannelOption.allSubObject.findOption(store.updateChannel)
                ) {
                    if (it.value == UpdateChannelOption.Beta.value) {
                        vm.viewModelScope.launchTry {
                            context.mainVm.dialogFlow.waitResult(
                                title = getString(R.string.version_channel),
                                text = getString(R.string.version_channel_desc),
                            )
                            storeFlow.update { s -> s.copy(updateChannel = it.value) }
                        }
                    } else {
                        storeFlow.update { s -> s.copy(updateChannel = it.value) }
                    }
                }

                Row(
                    modifier = Modifier
                        .clickable(
                            onClick = throttle(fn = context.mainVm.viewModelScope.launchAsFn {
                                if (context.mainVm.updateStatus.checkUpdatingFlow.value) return@launchAsFn
                                val newVersion = context.mainVm.updateStatus.checkUpdate()
                                if (newVersion == null) {
                                    toast(getString(R.string.no_update))
                                }
                            })
                        )
                        .fillMaxWidth()
                        .itemPadding(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = getString(R.string.check_for_update),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    RotatingLoadingIcon(loading = checkUpdating)
                }
            }

            Text(
                text = getString(R.string.other),
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            SettingItem(title = getString(R.string.advanced_settings), onClick = {
                navController.toDestinationsNavigator().navigate(AdvancedPageDestination)
            })

            SettingItem(title = getString(R.string.about), onClick = {
                navController.toDestinationsNavigator().navigate(AboutPageDestination)
            })

            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }
}
