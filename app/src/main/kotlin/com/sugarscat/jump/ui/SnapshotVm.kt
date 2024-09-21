package com.sugarscat.jump.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import com.sugarscat.jump.db.DbSet
import com.sugarscat.jump.ui.component.UploadOptions
import com.sugarscat.jump.util.IMPORT_SHORT_URL

class SnapshotVm : ViewModel() {
    val snapshotsState = DbSet.snapshotDao.query()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val uploadOptions = UploadOptions(
        scope = viewModelScope,
        showHref = { IMPORT_SHORT_URL + it.id }
    )
}