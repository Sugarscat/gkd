package com.sugarscat.jump.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramcosta.composedestinations.generated.destinations.GlobalRulePageDestination
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import com.sugarscat.jump.db.DbSet
import com.sugarscat.jump.util.map
import com.sugarscat.jump.util.subsIdToRawFlow
import com.sugarscat.jump.util.subsItemsFlow

class GlobalRuleVm (stateHandle: SavedStateHandle) : ViewModel() {
    private val args = GlobalRulePageDestination.argsFrom(stateHandle)
    val subsItemFlow =
        subsItemsFlow.map(viewModelScope) { s -> s.find { v -> v.id == args.subsItemId } }
    val subsRawFlow = subsIdToRawFlow.map(viewModelScope) { s -> s[args.subsItemId] }

    val subsConfigsFlow = DbSet.subsConfigDao.queryGlobalGroupTypeConfig(args.subsItemId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

}