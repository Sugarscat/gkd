package com.sugarscat.jump.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import com.blankj.utilcode.util.StringUtils.getString
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.AppItemPageDestination
import com.ramcosta.composedestinations.generated.destinations.GlobalRulePageDestination
import com.ramcosta.composedestinations.utils.toDestinationsNavigator
import com.sugarscat.jump.MainActivity
import com.sugarscat.jump.R
import com.sugarscat.jump.ui.component.EmptyText
import com.sugarscat.jump.ui.component.updateDialogOptions
import com.sugarscat.jump.ui.style.EmptyHeight
import com.sugarscat.jump.ui.style.itemPadding
import com.sugarscat.jump.util.LocalNavController
import com.sugarscat.jump.util.ProfileTransitions
import com.sugarscat.jump.util.appInfoCacheFlow
import com.sugarscat.jump.util.ruleSummaryFlow
import com.sugarscat.jump.util.throttle

@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun SlowGroupPage() {
    val context = LocalContext.current as MainActivity
    val navController = LocalNavController.current
    val ruleSummary by ruleSummaryFlow.collectAsState()
    val appInfoCache by appInfoCacheFlow.collectAsState()

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = {
                        navController.popBackStack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                title = {
                    Text(
                        text = if (ruleSummary.slowGroupCount > 0) getString(
                            R.string.slow_query_info,
                            ruleSummary.slowGroupCount
                        ) else getString(
                            R.string.slow_query
                        )
                    )
                },
                actions = {
                    IconButton(onClick = throttle {
                        context.mainVm.dialogFlow.updateDialogOptions(
                            title = getString(R.string.slow_query),
                            text = getString(R.string.slow_query_desc),
                        )
                    }) {
                        Icon(Icons.Outlined.Info, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(
                ruleSummary.slowGlobalGroups,
                { (_, r) -> "${r.subsItem.id}-${r.group.key}" }
            ) { (group, rule) ->
                SlowGroupCard(
                    modifier = Modifier
                        .clickable(onClick = throttle {
                            navController
                                .toDestinationsNavigator()
                                .navigate(
                                    GlobalRulePageDestination(
                                        rule.subsItem.id,
                                        group.key
                                    )
                                )
                        })
                        .itemPadding(),
                    title = group.name,
                    desc = "${rule.rawSubs.name}/" + getString(R.string.global_rules)
                )
            }
            items(
                ruleSummary.slowAppGroups,
                { (_, r) -> "${r.subsItem.id}-${r.appId}-${r.group.key}" }
            ) { (group, rule) ->
                SlowGroupCard(
                    modifier = Modifier
                        .clickable(onClick = throttle {
                            navController
                                .toDestinationsNavigator()
                                .navigate(
                                    AppItemPageDestination(
                                        rule.subsItem.id,
                                        rule.app.id,
                                        group.key
                                    )
                                )
                        })
                        .itemPadding(),
                    title = group.name,
                    desc = "${rule.rawSubs.name}/" + getString(R.string.global_rules)
                            + "/${appInfoCache[rule.app.id]?.name ?: rule.app.name ?: rule.app.id}"
                )
            }
            item {
                Spacer(modifier = Modifier.height(EmptyHeight))
                if (ruleSummary.slowGroupCount == 0) {
                    EmptyText(text = getString(R.string.no_rules))
                }
            }
        }
    }
}

@Composable
fun SlowGroupCard(title: String, desc: String, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null
        )
    }
}