package com.sugarscat.jump.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramcosta.composedestinations.generated.destinations.CategoryPageDestination
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import com.sugarscat.jump.db.DbSet
import com.sugarscat.jump.util.map
import com.sugarscat.jump.util.subsIdToRawFlow
import com.sugarscat.jump.util.subsItemsFlow

class CategoryVm (stateHandle: SavedStateHandle) : ViewModel() {
    private val args = CategoryPageDestination.argsFrom(stateHandle)

    val subsItemFlow =
        subsItemsFlow.map(viewModelScope) { subsItems -> subsItems.find { s -> s.id == args.subsItemId } }

    val subsRawFlow = subsIdToRawFlow.map(viewModelScope) { m -> m[args.subsItemId] }

    val categoryConfigsFlow = DbSet.categoryConfigDao.queryConfig(args.subsItemId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}