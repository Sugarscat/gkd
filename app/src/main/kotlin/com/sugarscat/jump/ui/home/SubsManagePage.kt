package com.sugarscat.jump.ui.home

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pullrefresh.PullRefreshIndicator
import androidx.compose.material3.pullrefresh.pullRefresh
import androidx.compose.material3.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blankj.utilcode.util.StringUtils.getString
import com.dylanc.activityresult.launcher.launchForResult
import com.sugarscat.jump.MainActivity
import com.sugarscat.jump.R
import com.sugarscat.jump.data.Value
import com.sugarscat.jump.data.deleteSubscription
import com.sugarscat.jump.data.exportData
import com.sugarscat.jump.data.importData
import com.sugarscat.jump.db.DbSet
import com.sugarscat.jump.ui.component.SubsItemCard
import com.sugarscat.jump.ui.component.TextMenu
import com.sugarscat.jump.ui.component.waitResult
import com.sugarscat.jump.ui.style.itemVerticalPadding
import com.sugarscat.jump.util.LOCAL_SUBS_ID
import com.sugarscat.jump.util.SafeR
import com.sugarscat.jump.util.UpdateTimeOption
import com.sugarscat.jump.util.checkSubsUpdate
import com.sugarscat.jump.util.findOption
import com.sugarscat.jump.util.launchAsFn
import com.sugarscat.jump.util.launchTry
import com.sugarscat.jump.util.map
import com.sugarscat.jump.util.saveFileToDownloads
import com.sugarscat.jump.util.shareFile
import com.sugarscat.jump.util.storeFlow
import com.sugarscat.jump.util.subsIdToRawFlow
import com.sugarscat.jump.util.subsItemsFlow
import com.sugarscat.jump.util.subsRefreshingFlow
import com.sugarscat.jump.util.throttle
import com.sugarscat.jump.util.toast
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

val subsNav = BottomNavItem(
    label = getString(R.string.nav_subscription),
    icon = Icons.AutoMirrored.Filled.FormatListBulleted
)

@Composable
fun useSubsManagePage(): ScaffoldExt {
    val context = LocalContext.current as MainActivity

    val vm = viewModel<HomeVm>()
    val subItems by subsItemsFlow.collectAsState()
    val subsIdToRaw by subsIdToRawFlow.collectAsState()

    var orderSubItems by remember {
        mutableStateOf(subItems)
    }
    LaunchedEffect(subItems) {
        orderSubItems = subItems
    }

    val refreshing by subsRefreshingFlow.collectAsState()
    val pullRefreshState = rememberPullRefreshState(refreshing, { checkSubsUpdate(true) })
    var isSelectedMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    val draggedFlag = remember { Value(false) }
    LaunchedEffect(key1 = isSelectedMode) {
        if (!isSelectedMode && selectedIds.isNotEmpty()) {
            selectedIds = emptySet()
        }
    }
    if (isSelectedMode) {
        BackHandler {
            isSelectedMode = false
        }
    }
    LaunchedEffect(key1 = subItems.size) {
        if (subItems.size <= 1) {
            isSelectedMode = false
        }
    }

    var showSettingsDlg by remember { mutableStateOf(false) }
    if (showSettingsDlg) {
        AlertDialog(
            onDismissRequest = { showSettingsDlg = false },
            title = { Text(getString(R.string.subscription_settings)) },
            text = {
                val store by storeFlow.collectAsState()
                TextMenu(
                    modifier = Modifier.padding(0.dp, itemVerticalPadding),
                    title = getString(R.string.update_subscription),
                    option = UpdateTimeOption.allSubObject.findOption(store.updateSubsInterval)
                ) {
                    storeFlow.update { s -> s.copy(updateSubsInterval = it.value) }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDlg = false }) {
                    Text(getString(R.string.close))
                }
            }
        )
    }

    ShareDataDialog(vm)
    vm.inputSubsLinkOption.ContentDialog()

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    return ScaffoldExt(
        navItem = subsNav,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(scrollBehavior = scrollBehavior, navigationIcon = {
                if (isSelectedMode) {
                    IconButton(onClick = { isSelectedMode = false }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                        )
                    }
                }
            }, title = {
                if (isSelectedMode) {
                    Text(
                        text = if (selectedIds.isNotEmpty()) selectedIds.size.toString() else "",
                    )
                } else {
                    Text(
                        text = subsNav.label,
                    )
                }
            }, actions = {
                var expanded by remember { mutableStateOf(false) }
                if (isSelectedMode) {
                    val canDeleteIds = if (selectedIds.contains(LOCAL_SUBS_ID)) {
                        selectedIds - LOCAL_SUBS_ID
                    } else {
                        selectedIds
                    }
                    if (canDeleteIds.isNotEmpty()) {
                        val text = getString(
                            R.string.delete_multiple_subscriptions_tip,
                            canDeleteIds.size
                        ).let {
                            if (selectedIds.contains(LOCAL_SUBS_ID))
                                "$it\n" + getString(R.string.not_include_local_subscriptions)
                            else it
                        }
                        IconButton(onClick = vm.viewModelScope.launchAsFn {
                            context.mainVm.dialogFlow.waitResult(
                                title = getString(R.string.delete_subscription),
                                text = text,
                                error = true,
                            )
                            deleteSubscription(*canDeleteIds.toLongArray())
                            selectedIds = selectedIds - canDeleteIds
                            if (selectedIds.size == canDeleteIds.size) {
                                isSelectedMode = false
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = null,
                            )
                        }
                    }
                    IconButton(onClick = {
                        vm.showShareDataIdsFlow.value = selectedIds
                    }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                        )
                    }
                    IconButton(onClick = {
                        expanded = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = null,
                        )
                    }
                } else {
                    IconButton(onClick = throttle {
                        if (storeFlow.value.enableMatch) {
                            toast(getString(R.string.pause_rule_matching))
                        } else {
                            toast(getString(R.string.enable_rule_matching))
                        }
                        storeFlow.update { s -> s.copy(enableMatch = !s.enableMatch) }
                    }) {
                        val scope = rememberCoroutineScope()
                        val enableMatch by remember {
                            storeFlow.map(scope) { it.enableMatch }
                        }.collectAsState()
                        val id = if (enableMatch) SafeR.ic_flash_on else SafeR.ic_flash_off
                        Icon(
                            painter = painterResource(id = id),
                            contentDescription = null,
                        )
                    }
                    IconButton(onClick = {
                        showSettingsDlg = true
                    }) {
                        Icon(
                            painter = painterResource(id = SafeR.ic_page_info),
                            contentDescription = null,
                        )
                    }
                    IconButton(onClick = {
                        if (subsRefreshingFlow.value) {
                            toast(getString(R.string.refreshing))
                        } else {
                            expanded = true
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = null,
                        )
                    }
                }
                Box(
                    modifier = Modifier.wrapContentSize(Alignment.TopStart)
                ) {
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        if (isSelectedMode) {
                            DropdownMenuItem(
                                text = {
                                    Text(text = getString(R.string.select_all))
                                },
                                onClick = {
                                    expanded = false
                                    selectedIds = subItems.map { it.id }.toSet()
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(text = getString(R.string.counter_election))
                                },
                                onClick = {
                                    expanded = false
                                    val newSelectedIds =
                                        subItems.map { it.id }.toSet() - selectedIds
                                    if (newSelectedIds.isEmpty()) {
                                        isSelectedMode = false
                                    }
                                    selectedIds = newSelectedIds
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                    )
                                },
                                text = {
                                    Text(text = getString(R.string.import_data))
                                },
                                onClick = vm.viewModelScope.launchAsFn(Dispatchers.IO) {
                                    expanded = false
                                    val result =
                                        context.launcher.launchForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                            addCategory(Intent.CATEGORY_OPENABLE)
                                            type = "application/zip"
                                        })
                                    val uri = result.data?.data
                                    if (uri == null) {
                                        toast(getString(R.string.no_file_selected))
                                        return@launchAsFn
                                    }
                                    importData(uri)
                                },
                            )
                        }
                    }
                }
            })
        },
        floatingActionButton = {
            if (!isSelectedMode) {
                FloatingActionButton(onClick = {
                    if (subsRefreshingFlow.value) {
                        toast(getString(R.string.refreshing))
                        return@FloatingActionButton
                    }
                    vm.viewModelScope.launchTry {
                        val url = vm.inputSubsLinkOption.getResult() ?: return@launchTry
                        vm.addOrModifySubs(url)
                    }
                }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "info",
                    )
                }
            }
        },
    ) { padding ->
        val lazyListState = rememberLazyListState()
        val reorderableLazyColumnState =
            rememberReorderableLazyListState(lazyListState) { from, to ->
                orderSubItems = orderSubItems.toMutableList().apply {
                    add(to.index, removeAt(from.index))
                    forEachIndexed { index, subsItem ->
                        if (subsItem.order != index) {
                            this[index] = subsItem.copy(order = index)
                        }
                    }
                }.toImmutableList()
                draggedFlag.value = true
            }
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .pullRefresh(pullRefreshState, subItems.isNotEmpty())
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(orderSubItems, { _, subItem -> subItem.id }) { index, subItem ->
                    val canDrag = !refreshing && orderSubItems.size > 1
                    ReorderableItem(
                        reorderableLazyColumnState,
                        key = subItem.id,
                        enabled = canDrag,
                    ) {
                        val interactionSource = remember { MutableInteractionSource() }
                        SubsItemCard(
                            modifier = Modifier.longPressDraggableHandle(
                                enabled = canDrag,
                                interactionSource = interactionSource,
                                onDragStarted = {
                                    if (orderSubItems.size > 1 && !isSelectedMode) {
                                        isSelectedMode = true
                                        selectedIds = setOf(subItem.id)
                                    }
                                },
                                onDragStopped = {
                                    if (draggedFlag.value) {
                                        draggedFlag.value = false
                                        isSelectedMode = false
                                        selectedIds = emptySet()
                                    }
                                    val changeItems = orderSubItems.filter { newItem ->
                                        subItems.find { oldItem -> oldItem.id == newItem.id }?.order != newItem.order
                                    }
                                    if (changeItems.isNotEmpty()) {
                                        vm.viewModelScope.launchTry {
                                            DbSet.subsItemDao.batchUpdateOrder(changeItems)
                                        }
                                    }
                                },
                            ),
                            interactionSource = interactionSource,
                            subsItem = subItem,
                            subscription = subsIdToRaw[subItem.id],
                            index = index + 1,
                            vm = vm,
                            isSelectedMode = isSelectedMode,
                            isSelected = selectedIds.contains(subItem.id),
                            onCheckedChange = { checked ->
                                vm.viewModelScope.launch {
                                    DbSet.subsItemDao.updateEnable(subItem.id, checked)
                                }
                            },
                            onSelectedChange = {
                                val newSelectedIds = if (selectedIds.contains(subItem.id)) {
                                    selectedIds.toMutableSet().apply {
                                        remove(subItem.id)
                                    }
                                } else {
                                    selectedIds + subItem.id
                                }
                                selectedIds = newSelectedIds
                                if (newSelectedIds.isEmpty()) {
                                    isSelectedMode = false
                                }
                            },
                        )
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
            PullRefreshIndicator(
                refreshing = refreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

@Composable
private fun ShareDataDialog(vm: HomeVm) {
    val context = LocalContext.current as MainActivity
    val showShareDataIds = vm.showShareDataIdsFlow.collectAsState().value
    if (showShareDataIds != null) {
        Dialog(onDismissRequest = { vm.showShareDataIdsFlow.value = null }) {
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
                            vm.showShareDataIdsFlow.value = null
                            vm.viewModelScope.launchTry(Dispatchers.IO) {
                                val file = exportData(showShareDataIds)
                                context.shareFile(file, getString(R.string.share_data_files))
                            }
                        })
                        .then(modifier)
                )
                Text(
                    text = getString(R.string.save_to_downloads),
                    modifier = Modifier
                        .clickable(onClick = throttle {
                            vm.showShareDataIdsFlow.value = null
                            vm.viewModelScope.launchTry(Dispatchers.IO) {
                                val file = exportData(showShareDataIds)
                                context.saveFileToDownloads(file)
                            }
                        })
                        .then(modifier)
                )
            }
        }
    }
}