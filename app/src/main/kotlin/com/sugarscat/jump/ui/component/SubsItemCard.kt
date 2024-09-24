package com.sugarscat.jump.ui.component

import android.view.MotionEvent
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.StringUtils.getString
import com.ramcosta.composedestinations.generated.destinations.CategoryPageDestination
import com.ramcosta.composedestinations.generated.destinations.GlobalRulePageDestination
import com.ramcosta.composedestinations.generated.destinations.SubsPageDestination
import com.ramcosta.composedestinations.utils.toDestinationsNavigator
import com.sugarscat.jump.MainActivity
import com.sugarscat.jump.R
import com.sugarscat.jump.data.RawSubscription
import com.sugarscat.jump.data.SubsItem
import com.sugarscat.jump.data.deleteSubscription
import com.sugarscat.jump.ui.home.HomeVm
import com.sugarscat.jump.util.LOCAL_SUBS_ID
import com.sugarscat.jump.util.LocalNavController
import com.sugarscat.jump.util.formatTimeAgo
import com.sugarscat.jump.util.launchTry
import com.sugarscat.jump.util.map
import com.sugarscat.jump.util.openUri
import com.sugarscat.jump.util.subsLoadErrorsFlow
import com.sugarscat.jump.util.subsRefreshErrorsFlow
import com.sugarscat.jump.util.subsRefreshingFlow
import com.sugarscat.jump.util.throttle
import com.sugarscat.jump.util.toast
import kotlinx.coroutines.Dispatchers


@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SubsItemCard(
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource,
    subsItem: SubsItem,
    subscription: RawSubscription?,
    index: Int,
    vm: HomeVm,
    isSelectedMode: Boolean,
    isSelected: Boolean,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    onSelectedChange: (() -> Unit)? = null,
) {
    val density = LocalDensity.current
    val subsLoadError by remember(subsItem.id) {
        subsLoadErrorsFlow.map(vm.viewModelScope) { it[subsItem.id] }
    }.collectAsState()
    val subsRefreshError by remember(subsItem.id) {
        subsRefreshErrorsFlow.map(vm.viewModelScope) { it[subsItem.id] }
    }.collectAsState()
    val subsRefreshing by subsRefreshingFlow.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    val dragged by interactionSource.collectIsDraggedAsState()
    var clickPositionX by remember {
        mutableStateOf(0.dp)
    }
    val onClick = {
        if (!dragged) {
            if (isSelectedMode) {
                onSelectedChange?.invoke()
            } else if (!subsRefreshingFlow.value) {
                expanded = true
            }
        }
    }
    Card(
        onClick = onClick,
        modifier = modifier
            .padding(16.dp, 2.dp)
            .pointerInteropFilter { event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    clickPositionX = with(density) { event.x.toDp() }
                }
                false
            },
        shape = MaterialTheme.shapes.small,
        interactionSource = interactionSource,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                Color.Unspecified
            }
        ),
    ) {
        SubsMenuItem(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            subItem = subsItem,
            subscription = subscription,
            offsetX = clickPositionX,
            vm = vm
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (subscription != null) {
                    Text(
                        text = index.toString() + ". " + (subscription.name),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = subsItem.sourceText,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = formatTimeAgo(subsItem.mtime),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (subsItem.id >= 0) {
                            Text(
                                text = "v" + (subscription.version.toString()),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    Text(
                        text = subscription.numText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (subscription.groupsSize == 0) {
                            LocalContentColor.current.copy(alpha = 0.5f)
                        } else {
                            LocalContentColor.current
                        }
                    )
                } else {
                    Text(
                        text = "${index}. id:${subsItem.id}",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val color = if (subsLoadError != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        Color.Unspecified
                    }
                    Text(
                        text = subsLoadError?.message
                            ?: if (subsRefreshing) getString(R.string.loading)
                            else getString(R.string.file_does_not_exist),
                        style = MaterialTheme.typography.bodyMedium,
                        color = color
                    )
                }
                if (subsRefreshError != null) {
                    Text(
                        text = getString(R.string.loading_error) + "${subsRefreshError?.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Switch(
                checked = subsItem.enable,
                enabled = !isSelectedMode,
                onCheckedChange = if (isSelectedMode) null else onCheckedChange,
            )
        }
    }
}

@Composable
private fun SubsMenuItem(
    expanded: Boolean,
    onExpandedChange: ((Boolean) -> Unit),
    subItem: SubsItem,
    subscription: RawSubscription?,
    offsetX: Dp,
    vm: HomeVm
) {
    val navController = LocalNavController.current
    val context = LocalContext.current as MainActivity
    val density = LocalDensity.current
    var halfMenuWidth by remember {
        mutableStateOf(0.dp)
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { onExpandedChange(false) },
        modifier = Modifier.onGloballyPositioned {
            halfMenuWidth = with(density) { it.size.width.toDp() } / 2
        },
        offset = DpOffset(if (offsetX < halfMenuWidth) 0.dp else offsetX - halfMenuWidth, 0.dp)
    ) {
        if (subscription != null) {
            if (subItem.id < 0 || subscription.apps.isNotEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(text = getString(R.string.app_rules))
                    },
                    onClick = throttle {
                        onExpandedChange(false)
                        navController.toDestinationsNavigator()
                            .navigate(SubsPageDestination(subItem.id))
                    }
                )
            }
            if (subItem.id < 0 || subscription.categories.isNotEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(text = getString(R.string.rule_category))
                    },
                    onClick = throttle {
                        onExpandedChange(false)
                        navController.toDestinationsNavigator()
                            .navigate(CategoryPageDestination(subItem.id))
                    }
                )
            }
            if (subItem.id < 0 || subscription.globalGroups.isNotEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(text = getString(R.string.global_rules))
                    },
                    onClick = throttle {
                        onExpandedChange(false)
                        navController.toDestinationsNavigator()
                            .navigate(GlobalRulePageDestination(subItem.id))
                    }
                )
            }
        }
        subscription?.supportUri?.let { supportUri ->
            DropdownMenuItem(
                text = {
                    Text(text = getString(R.string.problem_feedback))
                },
                onClick = {
                    onExpandedChange(false)
                    context.openUri(supportUri)
                }
            )
        }
        DropdownMenuItem(
            text = {
                Text(text = getString(R.string.export_data))
            },
            onClick = {
                onExpandedChange(false)
                vm.viewModelScope.launchTry(Dispatchers.IO) {
                    vm.showShareDataIdsFlow.value = setOf(subItem.id)
                }
            }
        )
        subItem.updateUrl?.let {
            DropdownMenuItem(
                text = {
                    Text(text = getString(R.string.copy_link))
                },
                onClick = {
                    onExpandedChange(false)
                    ClipboardUtils.copyText(subItem.updateUrl)
                    toast(getString(R.string.copied))
                }
            )
            DropdownMenuItem(
                text = {
                    Text(text = getString(R.string.modify_link))
                },
                onClick = {
                    onExpandedChange(false)
                    vm.viewModelScope.launchTry {
                        val newUrl = vm.inputSubsLinkOption.getResult(initValue = it)
                        newUrl ?: return@launchTry
                        vm.addOrModifySubs(newUrl, subItem)
                    }
                }
            )
        }
        if (subItem.id != LOCAL_SUBS_ID) {
            DropdownMenuItem(
                text = {
                    Text(text = "删除订阅", color = MaterialTheme.colorScheme.error)
                },
                onClick = {
                    onExpandedChange(false)
                    vm.viewModelScope.launchTry {
                        context.mainVm.dialogFlow.waitResult(
                            title = getString(R.string.delete_subscription),
                            text = getString(
                                R.string.delete_subscription_tip,
                                subscription?.name ?: subItem.id
                            ),
                            error = true,
                        )
                        deleteSubscription(subItem.id)
                    }
                }
            )
        }
    }
}
