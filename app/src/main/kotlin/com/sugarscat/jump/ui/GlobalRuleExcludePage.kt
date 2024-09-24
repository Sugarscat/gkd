package com.sugarscat.jump.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blankj.utilcode.util.StringUtils.getString
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.sugarscat.jump.R
import com.sugarscat.jump.data.AppInfo
import com.sugarscat.jump.data.ExcludeData
import com.sugarscat.jump.data.RawSubscription
import com.sugarscat.jump.data.SubsConfig
import com.sugarscat.jump.data.stringify
import com.sugarscat.jump.db.DbSet
import com.sugarscat.jump.service.launcherAppId
import com.sugarscat.jump.ui.component.AppBarTextField
import com.sugarscat.jump.ui.component.EmptyText
import com.sugarscat.jump.ui.component.QueryPkgAuthCard
import com.sugarscat.jump.ui.component.TowLineText
import com.sugarscat.jump.ui.style.EmptyHeight
import com.sugarscat.jump.ui.style.appItemPadding
import com.sugarscat.jump.ui.style.menuPadding
import com.sugarscat.jump.util.LocalNavController
import com.sugarscat.jump.util.ProfileTransitions
import com.sugarscat.jump.util.SortTypeOption
import com.sugarscat.jump.util.launchTry
import com.sugarscat.jump.util.toast

@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun GlobalRuleExcludePage(subsItemId: Long, groupKey: Int) {
    val navController = LocalNavController.current
    val vm = viewModel<GlobalRuleExcludeVm>()
    val rawSubs = vm.rawSubsFlow.collectAsState().value
    val group = vm.groupFlow.collectAsState().value
    val excludeData = vm.excludeDataFlow.collectAsState().value
    val showAppInfos = vm.showAppInfosFlow.collectAsState().value
    val searchStr by vm.searchStrFlow.collectAsState()
    val showSystemApp by vm.showSystemAppFlow.collectAsState()
    val showHiddenApp by vm.showHiddenAppFlow.collectAsState()
    val sortType by vm.sortTypeFlow.collectAsState()

    var showEditDlg by remember {
        mutableStateOf(false)
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
    val listState = rememberLazyListState()
    LaunchedEffect(key1 = showAppInfos, block = {
        listState.animateScrollToItem(0)
    })
    var expanded by remember { mutableStateOf(false) }
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
            if (showSearchBar) {
                AppBarTextField(
                    value = searchStr,
                    onValueChange = { newValue -> vm.searchStrFlow.value = newValue.trim() },
                    hint = getString(R.string.input_app_name_id),
                    modifier = Modifier.focusRequester(focusRequester)
                )
            } else {
                TowLineText(
                    title = rawSubs?.name ?: subsItemId.toString(),
                    subTitle = (group?.name ?: groupKey.toString())
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
                    Icon(Icons.Default.Search, contentDescription = null)
                }
                IconButton(onClick = {
                    showEditDlg = true
                }) {
                    Icon(Icons.Outlined.Edit, contentDescription = null)
                }

                IconButton(onClick = {
                    expanded = true
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = null
                    )
                }
                Box(
                    modifier = Modifier
                        .wrapContentSize(Alignment.TopStart)
                ) {
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
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
                                    RadioButton(selected = sortType == sortOption,
                                        onClick = {
                                            vm.sortTypeFlow.value = sortOption
                                        }
                                    )
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
                                Text(getString(R.string.show_system_apps))
                            },
                            trailingIcon = {
                                Checkbox(
                                    checked = showSystemApp,
                                    onCheckedChange = {
                                        vm.showSystemAppFlow.value = !vm.showSystemAppFlow.value
                                    })
                            },
                            onClick = {
                                vm.showSystemAppFlow.value = !vm.showSystemAppFlow.value
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(getString(R.string.show_hidden_apps))
                            },
                            trailingIcon = {
                                Checkbox(
                                    checked = showHiddenApp,
                                    onCheckedChange = {
                                        vm.showHiddenAppFlow.value =
                                            !vm.showHiddenAppFlow.value
                                    })
                            },
                            onClick = {
                                vm.showHiddenAppFlow.value = !vm.showHiddenAppFlow.value
                            },
                        )
                    }
                }
            }
        })
    }, content = { paddingValues ->
        LazyColumn(modifier = Modifier.padding(paddingValues), state = listState) {
            items(showAppInfos, { it.id }) { appInfo ->
                Row(
                    modifier = Modifier
                        .height(IntrinsicSize.Min)
                        .appItemPadding(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (appInfo.icon != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(1f)
                        ) {
                            Image(
                                painter = rememberDrawablePainter(appInfo.icon),
                                contentDescription = null,
                                modifier = Modifier
                                    .matchParentSize()
                                    .padding(4.dp)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.Android,
                            contentDescription = null,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .fillMaxHeight()
                                .padding(4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(
                        modifier = Modifier
                            .padding(2.dp)
                            .fillMaxHeight()
                            .weight(1f),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = appInfo.name,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyLarge.let {
                                if (appInfo.isSystem) {
                                    it.copy(textDecoration = TextDecoration.Underline)
                                } else {
                                    it
                                }
                            }
                        )
                        Text(
                            text = appInfo.id,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    if (group != null) {
                        val checked = getChecked(excludeData, group, appInfo.id, appInfo)
                        if (checked != null) {
                            Switch(
                                checked = checked,
                                onCheckedChange = {
                                    vm.viewModelScope.launchTry {
                                        val subsConfig = (vm.subsConfigFlow.value ?: SubsConfig(
                                            type = SubsConfig.GlobalGroupType,
                                            subsItemId = subsItemId,
                                            groupKey = groupKey,
                                        )).copy(
                                            exclude = excludeData.copy(
                                                appIds = excludeData.appIds.toMutableMap().apply {
                                                    set(appInfo.id, !it)
                                                })
                                                .stringify()
                                        )
                                        DbSet.subsConfigDao.insert(subsConfig)
                                    }
                                },
                            )
                        } else {
                            Switch(
                                enabled = false,
                                checked = false,
                                onCheckedChange = {},
                            )
                        }
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(EmptyHeight))
                if (showAppInfos.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        EmptyText(text = getString(R.string.no_search_results))
                        Spacer(modifier = Modifier.height(EmptyHeight))
                    }
                }
                QueryPkgAuthCard()
            }
        }
    })

    if (group != null && showEditDlg) {
        var source by remember {
            mutableStateOf(
                excludeData.stringify()
            )
        }
        val oldSource = remember { source }
        AlertDialog(
            title = { Text(text = getString(R.string.edit_disabled)) },
            text = {
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = {
                        Text(
                            text = getString(R.string.edit_disabled_placeholder),
                            style = LocalTextStyle.current.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize)
                        )
                    },
                    maxLines = 10,
                    textStyle = LocalTextStyle.current.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize)
                )
                LaunchedEffect(null) {
                    focusRequester.requestFocus()
                }
            },
            onDismissRequest = {
                if (source.isEmpty()) {
                    showEditDlg = false
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDlg = false }) {
                    Text(text = getString(R.string.cancel))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (oldSource == source) {
                        toast(getString(R.string.prohibited_items_no_change))
                        showEditDlg = false
                        return@TextButton
                    }
                    showEditDlg = false
                    val subsConfig = (vm.subsConfigFlow.value ?: SubsConfig(
                        type = SubsConfig.GlobalGroupType,
                        subsItemId = subsItemId,
                        groupKey = groupKey,
                    )).copy(
                        exclude = ExcludeData.parse(source).stringify()
                    )
                    vm.viewModelScope.launchTry {
                        DbSet.subsConfigDao.insert(subsConfig)
                    }
                }) {
                    Text(text = getString(R.string.confirm))
                }
            },
        )
    }

}

// null - 内置禁用
// true - 启用
// false - 禁用
fun getChecked(
    excludeData: ExcludeData,
    group: RawSubscription.RawGlobalGroup,
    appId: String,
    appInfo: AppInfo? = null
): Boolean? {
    val enable = group.appIdEnable[appId]
    if (enable == false) {
        return null
    }
    excludeData.appIds[appId]?.let { return !it }
    if (enable == true) return true
    if (appInfo?.id == launcherAppId) {
        return group.matchLauncher ?: false
    }
    if (appInfo?.isSystem == true) {
        return group.matchSystemApp ?: false
    }
    return group.matchAnyApp ?: true
}