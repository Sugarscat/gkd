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
import com.ramcosta.composedestinations.generated.destinations.ActivityLogPageDestination
import com.ramcosta.composedestinations.generated.destinations.AuthA11YPageDestination
import com.ramcosta.composedestinations.generated.destinations.ClickLogPageDestination
import com.ramcosta.composedestinations.generated.destinations.SlowGroupPageDestination
import com.ramcosta.composedestinations.utils.toDestinationsNavigator
import com.sugarscat.jump.MainActivity
import com.sugarscat.jump.permission.notificationState
import com.sugarscat.jump.permission.requiredPermission
import com.sugarscat.jump.permission.writeSecureSettingsState
import com.sugarscat.jump.service.JumpAbService
import com.sugarscat.jump.service.ManageService
import com.sugarscat.jump.service.switchA11yService
import com.sugarscat.jump.ui.component.AuthCard
import com.sugarscat.jump.ui.component.SettingItem
import com.sugarscat.jump.ui.component.TextSwitch
import com.sugarscat.jump.ui.style.EmptyHeight
import com.sugarscat.jump.ui.style.itemPadding
import com.sugarscat.jump.util.HOME_PAGE_URL
import com.sugarscat.jump.util.LocalNavController
import com.sugarscat.jump.util.launchAsFn
import com.sugarscat.jump.util.openUri
import com.sugarscat.jump.util.ruleSummaryFlow
import com.sugarscat.jump.util.storeFlow
import com.sugarscat.jump.util.throttle

val controlNav = BottomNavItem(label = "主页", icon = Icons.Outlined.Home)

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
                IconButton(onClick = throttle { context.openUri(HOME_PAGE_URL) }) {
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
                    title = "服务状态",
                    subtitle = if (store.enableService) "无障碍服务正在运行" else "无障碍服务已关闭",
                    checked = store.enableService,
                    onCheckedChange = {
                        switchA11yService()
                    })
            }
            if (!writeSecureSettings && !a11yRunning) {
                AuthCard(
                    title = "无障碍授权",
                    desc = if (a11yBroken) "服务故障,请重新授权" else "授权使无障碍服务运行",
                    onAuthClick = {
                        navController.toDestinationsNavigator().navigate(AuthA11YPageDestination)
                    })
            }

            TextSwitch(
                title = "常驻通知",
                subtitle = "显示运行状态及统计数据",
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
                title = "触发记录",
                subtitle = "如误触可定位关闭规则",
                onClick = {
                    navController.toDestinationsNavigator().navigate(ClickLogPageDestination)
                }
            )

            if (store.enableActivityLog) {
                SettingItem(
                    title = "界面记录",
                    subtitle = "记录打开的应用及界面",
                    onClick = {
                        navController.toDestinationsNavigator().navigate(ActivityLogPageDestination)
                    }
                )
            }

            if (ruleSummary.slowGroupCount > 0) {
                SettingItem(
                    title = "耗时查询-${ruleSummary.slowGroupCount}",
                    subtitle = "可能导致触发缓慢或更多耗电",
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
                        text = "最近点击: $latestRecordDesc",
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
