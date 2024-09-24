package com.sugarscat.jump.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.StringUtils.getString
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.AppItemPageDestination
import com.ramcosta.composedestinations.utils.toDestinationsNavigator
import com.sugarscat.jump.MainActivity
import com.sugarscat.jump.R
import com.sugarscat.jump.data.RawSubscription
import com.sugarscat.jump.data.SubsConfig
import com.sugarscat.jump.db.DbSet
import com.sugarscat.jump.ui.component.AppBarTextField
import com.sugarscat.jump.ui.component.EmptyText
import com.sugarscat.jump.ui.component.QueryPkgAuthCard
import com.sugarscat.jump.ui.component.SubsAppCard
import com.sugarscat.jump.ui.component.TowLineText
import com.sugarscat.jump.ui.component.waitResult
import com.sugarscat.jump.ui.style.EmptyHeight
import com.sugarscat.jump.ui.style.menuPadding
import com.sugarscat.jump.util.LocalNavController
import com.sugarscat.jump.util.ProfileTransitions
import com.sugarscat.jump.util.SortTypeOption
import com.sugarscat.jump.util.appInfoCacheFlow
import com.sugarscat.jump.util.json
import com.sugarscat.jump.util.launchAsFn
import com.sugarscat.jump.util.launchTry
import com.sugarscat.jump.util.throttle
import com.sugarscat.jump.util.toast
import com.sugarscat.jump.util.updateSubscription
import li.songe.json5.encodeToJson5String


@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun SubsPage(
    subsItemId: Long,
) {
    val context = LocalContext.current as MainActivity
    val navController = LocalNavController.current

    val vm = viewModel<SubsVm>()
    val subsItem = vm.subsItemFlow.collectAsState().value
    val appAndConfigs by vm.filterAppAndConfigsFlow.collectAsState()
    val searchStr by vm.searchStrFlow.collectAsState()
    val appInfoCache by appInfoCacheFlow.collectAsState()

    val subsRaw = vm.subsRawFlow.collectAsState().value

    // 本地订阅
    val editable = subsItem?.id.let { it != null && it < 0 }

    var showAddDlg by remember {
        mutableStateOf(false)
    }

    var editRawApp by remember {
        mutableStateOf<RawSubscription.RawApp?>(null)
    }

    var showSearchBar by rememberSaveable {
        mutableStateOf(false)
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(key1 = showSearchBar, block = {
        if (showSearchBar && searchStr.isEmpty()) {
            focusRequester.requestFocus()
        }
    })
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var expanded by remember { mutableStateOf(false) }
    val showUninstallApp by vm.showUninstallAppFlow.collectAsState()
    val sortType by vm.sortTypeFlow.collectAsState()
    val listState = rememberLazyListState()
    var isFirstVisit by remember { mutableStateOf(false) }
    LaunchedEffect(
        appAndConfigs.size,
        sortType.value,
        appAndConfigs.fold(0) { acc, t -> 31 * acc + t.t0.id.hashCode() }
    ) {
        if (isFirstVisit) {
            listState.scrollToItem(0)
        } else {
            isFirstVisit = true
        }
    }

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
            }, title = {
                if (showSearchBar) {
                    AppBarTextField(
                        value = searchStr,
                        onValueChange = { newValue -> vm.searchStrFlow.value = newValue.trim() },
                        hint = getString(R.string.input_app_name_id),
                        modifier = Modifier.focusRequester(focusRequester)
                    )
                } else {
                    TowLineText(
                        title = subsRaw?.name ?: subsItemId.toString(),
                        subTitle = getString(R.string.app_rules),
                    )
                }
            }, actions = {
                if (showSearchBar) {
                    IconButton(onClick = {
                        if (vm.searchStrFlow.value.isEmpty()) {
                            showSearchBar = false
                        } else {
                            vm.searchStrFlow.value = ""
                        }
                    }) {
                        Icon(Icons.Outlined.Close, contentDescription = null)
                    }
                } else {
                    IconButton(onClick = {
                        showSearchBar = true
                    }) {
                        Icon(Icons.Outlined.Search, contentDescription = null)
                    }
                    IconButton(onClick = { expanded = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort, contentDescription = null
                        )
                    }
                    Box(
                        modifier = Modifier.wrapContentSize(Alignment.TopStart)
                    ) {
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            Text(
                                text = getString(R.string.sort),
                                modifier = Modifier.menuPadding(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            SortTypeOption.allSubObject.forEach { sortOption ->
                                DropdownMenuItem(
                                    text = {
                                        Text(sortOption.label)
                                    },
                                    trailingIcon = {
                                        RadioButton(
                                            selected = sortType == sortOption,
                                            onClick = {
                                                vm.sortTypeFlow.value = sortOption
                                            })
                                    },
                                    onClick = {
                                        vm.sortTypeFlow.value = sortOption
                                    },
                                )
                            }
                            Text(
                                text = getString(R.string.options),
                                modifier = Modifier.menuPadding(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(getString(R.string.show_uninstalled_apps))
                                },
                                trailingIcon = {
                                    Checkbox(checked = showUninstallApp, onCheckedChange = {
                                        vm.showUninstallAppFlow.value = it
                                    })
                                },
                                onClick = {
                                    vm.showUninstallAppFlow.value = !showUninstallApp
                                },
                            )
                        }
                    }

                }
            })
        },
        floatingActionButton = {
            if (editable) {
                FloatingActionButton(onClick = { showAddDlg = true }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding), state = listState
        ) {
            itemsIndexed(appAndConfigs, { i, a -> i.toString() + a.t0.id }) { _, a ->
                val (appRaw, subsConfig, enableSize) = a
                SubsAppCard(
                    rawApp = appRaw,
                    appInfo = appInfoCache[appRaw.id],
                    subsConfig = subsConfig,
                    enableSize = enableSize,
                    onClick = throttle {
                        navController.toDestinationsNavigator()
                            .navigate(AppItemPageDestination(subsItemId, appRaw.id))
                    },
                    onValueChange = throttle(fn = vm.viewModelScope.launchAsFn { enable ->
                        val newItem = subsConfig?.copy(
                            enable = enable
                        ) ?: SubsConfig(
                            enable = enable,
                            type = SubsConfig.AppType,
                            subsItemId = subsItemId,
                            appId = appRaw.id,
                        )
                        DbSet.subsConfigDao.insert(newItem)
                    }),
                    showMenu = editable,
                    onDelClick = throttle(fn = vm.viewModelScope.launchAsFn {
                        context.mainVm.dialogFlow.waitResult(
                            title = getString(R.string.delete_rule_group),
                            text = getString(
                                R.string.delete_rules_under_the_group_tip,
                                appInfoCache[appRaw.id]?.name ?: appRaw.name ?: appRaw.id
                            ),
                            error = true,
                        )
                        if (subsRaw != null && subsItem != null) {
                            updateSubscription(subsRaw.copy(apps = subsRaw.apps.filter { a -> a.id != appRaw.id }))
                            DbSet.subsItemDao.update(subsItem.copy(mtime = System.currentTimeMillis()))
                            DbSet.subsConfigDao.delete(subsItem.id, appRaw.id)
                            toast(getString(R.string.delete_success))
                        }
                    })
                )
            }
            item {
                Spacer(modifier = Modifier.height(EmptyHeight))
                if (appAndConfigs.isEmpty()) {
                    EmptyText(
                        text = if (searchStr.isNotEmpty()) getString(R.string.no_search_results)
                        else getString(R.string.no_rules),
                    )
                } else if (editable) {
                    Spacer(modifier = Modifier.height(EmptyHeight))
                }
                QueryPkgAuthCard()
            }
        }
    }


    if (showAddDlg && subsRaw != null && subsItem != null) {
        var source by remember {
            mutableStateOf("")
        }
        AlertDialog(title = { Text(text = getString(R.string.add_app_rules)) }, text = {
            OutlinedTextField(
                value = source,
                onValueChange = { source = it },
                maxLines = 10,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(text = getString(R.string.add_app_rules_tip)) },
            )
        }, onDismissRequest = {
            if (source.isEmpty()) {
                showAddDlg = false
            }
        }, confirmButton = {
            TextButton(onClick = {
                val newAppRaw = try {
                    RawSubscription.parseRawApp(source)
                } catch (e: Exception) {
                    LogUtils.d(e)
                    toast(getString(R.string.illegal_rule_tip, e.message))
                    return@TextButton
                }
                if (newAppRaw.groups.isEmpty()) {
                    toast(getString(R.string.cannot_add_empty_rule_group))
                    return@TextButton
                }
                if (newAppRaw.groups.any { s -> s.name.isBlank() }) {
                    toast(getString(R.string.rule_group_name_is_blank))
                    return@TextButton
                }
                val oldAppRawIndex = subsRaw.apps.indexOfFirst { a -> a.id == newAppRaw.id }
                val oldAppRaw = subsRaw.apps.getOrNull(oldAppRawIndex)
                if (oldAppRaw != null) {
                    // check same group name
                    newAppRaw.groups.forEach { g ->
                        if (oldAppRaw.groups.any { g0 -> g0.name == g.name }) {
                            toast(getString(R.string.has_same_rule_name, g.name))
                            return@TextButton
                        }
                    }
                }
                // 重写添加的规则的 key
                val initKey =
                    ((oldAppRaw?.groups ?: emptyList()).maxByOrNull { g -> g.key }?.key ?: -1) + 1
                val finalAppRaw = if (oldAppRaw != null) {
                    newAppRaw.copy(groups = oldAppRaw.groups + newAppRaw.groups.mapIndexed { i, g ->
                        g.copy(
                            key = initKey + i
                        )
                    })
                } else {
                    newAppRaw.copy(groups = newAppRaw.groups.mapIndexed { i, g ->
                        g.copy(
                            key = initKey + i
                        )
                    })
                }
                val newApps = if (oldAppRaw != null) {
                    subsRaw.apps.toMutableList().apply {
                        set(oldAppRawIndex, finalAppRaw)
                    }
                } else {
                    subsRaw.apps.toMutableList().apply {
                        add(finalAppRaw)
                    }
                }
                vm.viewModelScope.launchTry {
                    updateSubscription(
                        subsRaw.copy(
                            apps = newApps, version = subsRaw.version + 1
                        )
                    )
                    DbSet.subsItemDao.update(subsItem.copy(mtime = System.currentTimeMillis()))
                    showAddDlg = false
                    toast(getString(R.string.add_success))
                }
            }, enabled = source.isNotEmpty()) {
                Text(text = getString(R.string.add))
            }
        }, dismissButton = {
            TextButton(onClick = { showAddDlg = false }) {
                Text(text = getString(R.string.cancel))
            }
        })
    }

    val editAppRawVal = editRawApp
    if (editAppRawVal != null && subsItem != null && subsRaw != null) {
        var source by remember {
            mutableStateOf(json.encodeToJson5String(editAppRawVal))
        }
        AlertDialog(
            title = { Text(text = "编辑应用规则") },
            text = {
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    maxLines = 10,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = { Text(text = "请输入规则") },
                )
                LaunchedEffect(null) {
                    focusRequester.requestFocus()
                }
            },
            onDismissRequest = {
                if (source.isEmpty()) {
                    editRawApp = null
                }
            }, confirmButton = {
                TextButton(onClick = {
                    try {
                        val newAppRaw = RawSubscription.parseRawApp(source)
                        if (newAppRaw.id != editAppRawVal.id) {
                            toast("不允许修改规则id")
                            return@TextButton
                        }
                        val oldAppRawIndex =
                            subsRaw.apps.indexOfFirst { a -> a.id == editAppRawVal.id }
                        vm.viewModelScope.launchTry {
                            updateSubscription(
                                subsRaw.copy(
                                    apps = subsRaw.apps.toMutableList().apply {
                                        set(oldAppRawIndex, newAppRaw)
                                    }, version = subsRaw.version + 1
                                )
                            )
                            DbSet.subsItemDao.update(subsItem.copy(mtime = System.currentTimeMillis()))
                            editRawApp = null
                            toast("更新成功")
                        }
                    } catch (e: Exception) {
                        LogUtils.d(e)
                        toast("非法规则${e.message}")
                    }
                }, enabled = source.isNotEmpty()) {
                    Text(text = "添加")
                }
            }, dismissButton = {
                TextButton(onClick = { editRawApp = null }) {
                    Text(text = "取消")
                }
            })
    }
}