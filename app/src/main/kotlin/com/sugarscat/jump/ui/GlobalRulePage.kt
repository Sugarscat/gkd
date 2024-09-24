package com.sugarscat.jump.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.StringUtils.getString
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.GlobalRuleExcludePageDestination
import com.ramcosta.composedestinations.generated.destinations.ImagePreviewPageDestination
import com.ramcosta.composedestinations.utils.toDestinationsNavigator
import com.sugarscat.jump.MainActivity
import com.sugarscat.jump.R
import com.sugarscat.jump.data.RawSubscription
import com.sugarscat.jump.data.SubsConfig
import com.sugarscat.jump.db.DbSet
import com.sugarscat.jump.ui.component.EmptyText
import com.sugarscat.jump.ui.component.TowLineText
import com.sugarscat.jump.ui.component.waitResult
import com.sugarscat.jump.ui.style.EmptyHeight
import com.sugarscat.jump.ui.style.itemPadding
import com.sugarscat.jump.util.LocalNavController
import com.sugarscat.jump.util.ProfileTransitions
import com.sugarscat.jump.util.json
import com.sugarscat.jump.util.launchAsFn
import com.sugarscat.jump.util.launchTry
import com.sugarscat.jump.util.throttle
import com.sugarscat.jump.util.toast
import com.sugarscat.jump.util.updateSubscription
import kotlinx.coroutines.Dispatchers
import li.songe.json5.encodeToJson5String

@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun GlobalRulePage(subsItemId: Long, focusGroupKey: Int? = null) {
    val context = LocalContext.current as MainActivity
    val navController = LocalNavController.current
    val vm = viewModel<GlobalRuleVm>()
    val subsItem by vm.subsItemFlow.collectAsState()
    val rawSubs = vm.subsRawFlow.collectAsState().value
    val subsConfigs by vm.subsConfigsFlow.collectAsState()

    val editable = subsItemId < 0 && rawSubs != null && subsItem != null
    val globalGroups = rawSubs?.globalGroups ?: emptyList()

    var showAddDlg by remember { mutableStateOf(false) }
    val (menuGroupRaw, setMenuGroupRaw) = remember {
        mutableStateOf<RawSubscription.RawGlobalGroup?>(null)
    }
    val (editGroupRaw, setEditGroupRaw) = remember {
        mutableStateOf<RawSubscription.RawGlobalGroup?>(null)
    }
    val (showGroupItem, setShowGroupItem) = remember {
        mutableStateOf<RawSubscription.RawGlobalGroup?>(
            null
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
            }, title = {
                TowLineText(
                    title = rawSubs?.name ?: subsItemId.toString(),
                    subTitle = getString(R.string.global_rules)
                )
            })
        },
        floatingActionButton = {
            if (editable) {
                FloatingActionButton(onClick = { showAddDlg = true }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "add",
                    )
                }
            }
        },
    ) { paddingValues ->
        LazyColumn(modifier = Modifier.padding(paddingValues)) {
            items(globalGroups, { g -> g.key }) { group ->
                Row(
                    modifier = Modifier
                        .background(
                            if (group.key == focusGroupKey) MaterialTheme.colorScheme.inversePrimary else Color.Transparent
                        )
                        .clickable { setShowGroupItem(group) }
                        .itemPadding(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = group.name,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        if (group.valid) {
                            if (!group.desc.isNullOrBlank()) {
                                Text(
                                    text = group.desc,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Text(
                                    text = getString(R.string.no_desc),
                                    modifier = Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        } else {
                            Text(
                                text = getString(R.string.illegal_selector),
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))

                    var expanded by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .wrapContentSize(Alignment.TopStart)
                    ) {
                        IconButton(onClick = {
                            expanded = true
                        }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = null,
                            )
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(text = getString(R.string.copy))
                                },
                                onClick = {
                                    expanded = false
                                    val groupAppText = json.encodeToJson5String(group)
                                    ClipboardUtils.copyText(groupAppText)
                                    toast(getString(R.string.copied))
                                }
                            )
                            if (editable) {
                                DropdownMenuItem(
                                    text = {
                                        Text(text = getString(R.string.edit))
                                    },
                                    onClick = {
                                        expanded = false
                                        setEditGroupRaw(group)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(text = getString(R.string.edit_disabled))
                                },
                                onClick = throttle {
                                    expanded = false
                                    navController.toDestinationsNavigator().navigate(
                                        GlobalRuleExcludePageDestination(
                                            subsItemId,
                                            group.key
                                        )
                                    )
                                }
                            )
                            if (editable && rawSubs != null) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = getString(R.string.delete),
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        expanded = false
                                        vm.viewModelScope.launchTry {
                                            context.mainVm.dialogFlow.waitResult(
                                                title = getString(R.string.delete_rule_group),
                                                text = getString(
                                                    R.string.confirm_deletion_tip,
                                                    group.name
                                                ),
                                                error = true,
                                            )
                                            updateSubscription(
                                                rawSubs.copy(
                                                    globalGroups = rawSubs.globalGroups.filter { g -> g.key != group.key }
                                                )
                                            )
                                            val subsConfig =
                                                subsConfigs.find { it.groupKey == group.key }
                                            if (subsConfig != null) {
                                                DbSet.subsConfigDao.delete(subsConfig)
                                            }
                                            DbSet.subsItemDao.updateMtime(rawSubs.id)
                                        }
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))

                    val groupEnable = subsConfigs.find { c -> c.groupKey == group.key }?.enable
                        ?: group.enable ?: true
                    val subsConfig = subsConfigs.find { it.groupKey == group.key }
                    Switch(
                        checked = groupEnable, modifier = Modifier,
                        onCheckedChange = vm.viewModelScope.launchAsFn { enable ->
                            val newItem = (subsConfig?.copy(enable = enable) ?: SubsConfig(
                                type = SubsConfig.GlobalGroupType,
                                subsItemId = subsItemId,
                                groupKey = group.key,
                                enable = enable
                            ))
                            DbSet.subsConfigDao.insert(newItem)
                        }
                    )
                }
            }
            item {
                Spacer(modifier = Modifier.height(EmptyHeight))
                if (globalGroups.isEmpty()) {
                    EmptyText(text = getString(R.string.no_rules))
                } else if (editable) {
                    Spacer(modifier = Modifier.height(EmptyHeight))
                }
            }
        }
    }

    if (showAddDlg && rawSubs != null) {
        var source by remember {
            mutableStateOf("")
        }
        AlertDialog(title = { Text(text = getString(R.string.add_global_rule_group)) }, text = {
            OutlinedTextField(
                value = source,
                onValueChange = { source = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(text = getString(R.string.input_rule_group)) },
                maxLines = 10,
            )
        }, onDismissRequest = {
            if (source.isEmpty()) {
                showAddDlg = false
            }
        }, confirmButton = {
            TextButton(onClick = {
                val newGroup = try {
                    RawSubscription.parseRawGlobalGroup(source)
                } catch (e: Exception) {
                    toast(getString(R.string.illegal_rule_tip, e.message ?: e))
                    return@TextButton
                }
                if (newGroup.errorDesc != null) {
                    toast(newGroup.errorDesc!!)
                    return@TextButton
                }
                if (rawSubs.globalGroups.any { g -> g.name == newGroup.name }) {
                    toast(getString(R.string.has_same_rule_name, newGroup.name))
                    return@TextButton
                }
                val newKey = (rawSubs.globalGroups.maxByOrNull { g -> g.key }?.key ?: -1) + 1
                val newRawSubs = rawSubs.copy(
                    globalGroups = rawSubs.globalGroups.toMutableList()
                        .apply { add(newGroup.copy(key = newKey)) }
                )
                updateSubscription(newRawSubs)
                vm.viewModelScope.launchTry(Dispatchers.IO) {
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

    if (menuGroupRaw != null && rawSubs != null) {
        Dialog(onDismissRequest = { setMenuGroupRaw(null) }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
            ) {

                if (editable) {
                    Text(text = getString(R.string.delete_rule_group), modifier = Modifier
                        .clickable {
                            setMenuGroupRaw(null)

                        }
                        .padding(16.dp)
                        .fillMaxWidth(),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
    if (editGroupRaw != null && rawSubs != null) {
        var source by remember {
            mutableStateOf(json.encodeToJson5String(editGroupRaw))
        }
        val focusRequester = remember { FocusRequester() }
        val oldSource = remember { source }
        AlertDialog(
            title = { Text(text = getString(R.string.edit_rule_group)) },
            text = {
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = { Text(text = getString(R.string.input_rule_group)) },
                    maxLines = 10,
                )
                LaunchedEffect(null) {
                    focusRequester.requestFocus()
                }
            },
            onDismissRequest = {
                if (source.isEmpty()) {
                    setEditGroupRaw(null)
                }
            },
            dismissButton = {
                TextButton(onClick = { setEditGroupRaw(null) }) {
                    Text(text = getString(R.string.cancel))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (oldSource == source) {
                        setEditGroupRaw(null)
                        toast(getString(R.string.rule_no_change))
                        return@TextButton
                    }
                    val newGroupRaw = try {
                        RawSubscription.parseRawGlobalGroup(source)
                    } catch (e: Exception) {
                        LogUtils.d(e)
                        toast(getString(R.string.illegal_rule_tip, e.message))
                        return@TextButton
                    }
                    if (newGroupRaw.key != editGroupRaw.key) {
                        toast(getString(R.string.rule_group_key_cannot_change))
                        return@TextButton
                    }
                    if (newGroupRaw.errorDesc != null) {
                        toast(newGroupRaw.errorDesc!!)
                        return@TextButton
                    }
                    setEditGroupRaw(null)
                    val newGlobalGroups = rawSubs.globalGroups.toMutableList().apply {
                        val i = rawSubs.globalGroups.indexOfFirst { g -> g.key == newGroupRaw.key }
                        if (i >= 0) {
                            set(i, newGroupRaw)
                        }
                    }
                    updateSubscription(rawSubs.copy(globalGroups = newGlobalGroups))
                    vm.viewModelScope.launchTry(Dispatchers.IO) {
                        DbSet.subsItemDao.updateMtime(rawSubs.id)
                        toast(getString(R.string.update_success))
                    }
                }, enabled = source.isNotEmpty()) {
                    Text(text = getString(R.string.update))
                }
            },
        )
    }

    if (showGroupItem != null) {
        AlertDialog(
            onDismissRequest = { setShowGroupItem(null) },
            title = {
                Text(text = getString(R.string.rule_group_details))
            },
            text = {
                Column {
                    Text(text = showGroupItem.name)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = showGroupItem.desc ?: "")
                }
            },
            confirmButton = {
                if (showGroupItem.allExampleUrls.isNotEmpty()) {
                    TextButton(onClick = throttle {
                        setShowGroupItem(null)
                        navController.toDestinationsNavigator().navigate(
                            ImagePreviewPageDestination(
                                title = showGroupItem.name,
                                uris = showGroupItem.allExampleUrls.toTypedArray()
                            )
                        )
                    }) {
                        Text(text = getString(R.string.view_pictures))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { setShowGroupItem(null) }) {
                    Text(text = getString(R.string.close))
                }
            }
        )
    }
}