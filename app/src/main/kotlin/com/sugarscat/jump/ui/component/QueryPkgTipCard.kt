package com.sugarscat.jump.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.StringUtils.getString
import com.sugarscat.jump.MainActivity
import com.sugarscat.jump.R
import com.sugarscat.jump.permission.canQueryPkgState
import com.sugarscat.jump.permission.requiredPermission
import com.sugarscat.jump.ui.style.EmptyHeight
import com.sugarscat.jump.util.appRefreshingFlow
import com.sugarscat.jump.util.launchAsFn
import com.sugarscat.jump.util.throttle

@Composable
fun QueryPkgAuthCard() {
    val canQueryPkg by canQueryPkgState.stateFlow.collectAsState()
    if (!canQueryPkg) {
        val context = LocalContext.current as MainActivity
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = getString(R.string.show_apps_list_tip),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = throttle(fn = context.mainVm.viewModelScope.launchAsFn {
                requiredPermission(context, canQueryPkgState)
            })) {
                Text(text = getString(R.string.apply_for_permission))
            }
        }
    } else {
        val appRefreshing by appRefreshingFlow.collectAsState()
        if (appRefreshing) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(EmptyHeight / 2))
                CircularProgressIndicator()
            }
        }
    }
}