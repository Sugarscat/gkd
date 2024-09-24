package com.sugarscat.jump.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blankj.utilcode.util.StringUtils.getString
import com.ramcosta.composedestinations.generated.destinations.ActivityLogPageDestination
import com.ramcosta.composedestinations.generated.destinations.AuthA11YPageDestination
import com.ramcosta.composedestinations.generated.destinations.ClickLogPageDestination
import com.ramcosta.composedestinations.generated.destinations.SlowGroupPageDestination
import com.ramcosta.composedestinations.utils.toDestinationsNavigator
import com.sugarscat.jump.MainActivity
import com.sugarscat.jump.R
import com.sugarscat.jump.permission.notificationState
import com.sugarscat.jump.permission.requiredPermission
import com.sugarscat.jump.permission.writeSecureSettingsState
import com.sugarscat.jump.service.JumpAbService
import com.sugarscat.jump.service.ManageService
import com.sugarscat.jump.service.switchA11yService
import com.sugarscat.jump.ui.APP_WEBSITE
import com.sugarscat.jump.ui.component.AuthCard
import com.sugarscat.jump.ui.component.SettingItem
import com.sugarscat.jump.ui.component.TextSwitch
import com.sugarscat.jump.ui.style.EmptyHeight
import com.sugarscat.jump.ui.style.itemPadding
import com.sugarscat.jump.util.LocalNavController
import com.sugarscat.jump.util.launchAsFn
import com.sugarscat.jump.util.openUri
import com.sugarscat.jump.util.ruleSummaryFlow
import com.sugarscat.jump.util.storeFlow
import com.sugarscat.jump.util.throttle

val controlNav = BottomNavItem(label = getString(R.string.nav_home), icon = Icons.Outlined.Home)

@Composable
fun useControlPage(): ScaffoldExt {
    val context = LocalContext.current as MainActivity
    val navController = LocalNavController.current
    val vm = viewModel<HomeVm>()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val scrollState = rememberScrollState()
    val writeSecureSettings by writeSecureSettingsState.stateFlow.collectAsState()
    return ScaffoldExt(navItem = controlNav,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(scrollBehavior = scrollBehavior, title = {
                Text(
                    text = controlNav.label,
                )
            }, actions = {
                IconButton(onClick = throttle {
                    navController.toDestinationsNavigator().navigate(AuthA11YPageDestination)
                }) {
                    Icon(
                        imageVector = Icons.Outlined.RocketLaunch,
                        contentDescription = null,
                    )
                }
                IconButton(onClick = throttle { context.openUri(APP_WEBSITE) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                        contentDescription = null,
                    )
                }
            })
        }
    ) { padding ->
        val latestRecordDesc by vm.latestRecordDescFlow.collectAsState()
        val subsStatus by vm.subsStatusFlow.collectAsState()
        val store by storeFlow.collectAsState()
        val ruleSummary by ruleSummaryFlow.collectAsState()

        val a11yRunning by JumpAbService.isRunning.collectAsState()
        val manageRunning by ManageService.isRunning.collectAsState()
        val a11yServiceEnabled by com.sugarscat.jump.a11yServiceEnabledFlow.collectAsState()

        // 无障碍故障: 设置中无障碍开启, 但是实际 service 没有运行
        val a11yBroken = !writeSecureSettings && !a11yRunning && a11yServiceEnabled

        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(padding)
        ) {
            if (writeSecureSettings) {
                TextSwitch(
                    title = getString(R.string.service_status),
                    subtitle =
                    if (store.enableService)
                        getString(R.string.accessibility_is_running)
                    else
                        getString(R.string.accessibility_is_not_available),
                    checked = store.enableService,
                    onCheckedChange = {
                        switchA11yService()
                    })
            }
            if (!writeSecureSettings && !a11yRunning) {
                AuthCard(
                    title = getString(R.string.authorize_accessibility),
                    desc =
                    if (a11yBroken)
                        getString(R.string.authorization_failed)
                    else
                        getString(R.string.authorization_success),
                    onAuthClick = {
                        navController.toDestinationsNavigator().navigate(AuthA11YPageDestination)
                    })
            }

            TextSwitch(
                title = getString(R.string.resident_notice),
                subtitle = getString(R.string.resident_notice_desc),
                checked = manageRunning && store.enableStatusService,
                onCheckedChange = vm.viewModelScope.launchAsFn<Boolean> {
                    if (it) {
                        requiredPermission(context, notificationState)
                        storeFlow.value = store.copy(
                            enableStatusService = true
                        )
                        ManageService.start(context)
                    } else {
                        storeFlow.value = store.copy(
                            enableStatusService = false
                        )
                        ManageService.stop(context)
                    }
                })

            SettingItem(
                title = getString(R.string.triggered_record),
                subtitle = getString(R.string.triggered_record_desc),
                onClick = {
                    navController.toDestinationsNavigator().navigate(ClickLogPageDestination)
                }
            )

            if (store.enableActivityLog) {
                SettingItem(
                    title = getString(R.string.interface_record),
                    subtitle = getString(R.string.interface_record_desc),
                    onClick = {
                        navController.toDestinationsNavigator().navigate(ActivityLogPageDestination)
                    }
                )
            }

            if (ruleSummary.slowGroupCount > 0) {
                SettingItem(
                    title = getString(R.string.time_consuming, ruleSummary.slowGroupCount),
                    subtitle = getString(R.string.time_consuming_desc),
                    onClick = {
                        navController.toDestinationsNavigator().navigate(SlowGroupPageDestination)
                    }
                )
            }
            HorizontalDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .itemPadding()
            ) {
                Text(
                    text = subsStatus,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (latestRecordDesc != null) {
                    Text(
                        text = getString(R.string.recent_clicks, latestRecordDesc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }
}
