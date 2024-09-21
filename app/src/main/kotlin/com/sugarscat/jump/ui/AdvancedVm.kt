package com.sugarscat.jump.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import com.sugarscat.jump.db.DbSet
import com.sugarscat.jump.ui.component.UploadOptions

class AdvancedVm : ViewModel() {
    val snapshotCountFlow =
        DbSet.snapshotDao.count().stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val uploadOptions = UploadOptions(viewModelScope)
}