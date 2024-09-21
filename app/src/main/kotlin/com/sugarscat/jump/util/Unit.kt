package com.sugarscat.jump.util

import android.util.TypedValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.sugarscat.jump.app

val Dp.px: Float
    get() {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value, com.sugarscat.jump.app.resources.displayMetrics
        )
    }

val TextUnit.px: Float
    get() {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            value, com.sugarscat.jump.app.resources.displayMetrics
        )
    }

