package com.sugarscat.jump.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramcosta.composedestinations.generated.destinations.AppItemPageDestination
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.sugarscat.jump.data.RawSubscription
import com.sugarscat.jump.db.DbSet
import com.sugarscat.jump.util.map
import com.sugarscat.jump.util.subsIdToRawFlow
import com.sugarscat.jump.util.subsItemsFlow

class AppItemVm (stateHandle: SavedStateHandle) : ViewModel() {
    private val args = AppItemPageDestination.argsFrom(stateHandle)

    val subsItemFlow =
        subsItemsFlow.map { subsItems -> subsItems.find { s -> s.id == args.subsItemId } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val subsRawFlow = subsIdToRawFlow.map(viewModelScope) { s -> s[args.subsItemId] }

    val subsConfigsFlow = DbSet.subsConfigDao.queryAppGroupTypeConfig(args.subsItemId, args.appId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val categoryConfigsFlow = DbSet.categoryConfigDao.queryConfig(args.subsItemId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val subsAppFlow =
        subsIdToRawFlow.map(viewModelScope) { subsIdToRaw ->
            subsIdToRaw[args.subsItemId]?.apps?.find { it.id == args.appId }
                ?: RawSubscription.RawApp(id = args.appId, name = null)
        }

}