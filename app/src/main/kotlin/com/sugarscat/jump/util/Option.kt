package com.sugarscat.jump.util

import com.blankj.utilcode.util.StringUtils.getString
import com.sugarscat.jump.R

sealed interface Option<T> {
    val value: T
    val label: String
}

fun <V, T : Option<V>> Array<T>.findOption(value: V): T {
    return find { it.value == value } ?: first()
}

@Suppress("UNCHECKED_CAST")
val <T> Option<T>.allSubObject: Array<Option<T>>
    get() = when (this) {
        is SortTypeOption -> SortTypeOption.allSubObject
        is UpdateTimeOption -> UpdateTimeOption.allSubObject
        is DarkThemeOption -> DarkThemeOption.allSubObject
        is EnableGroupOption -> EnableGroupOption.allSubObject
        is RuleSortOption -> RuleSortOption.allSubObject
        is UpdateChannelOption -> UpdateChannelOption.allSubObject
    } as Array<Option<T>>

sealed class SortTypeOption(override val value: Int, override val label: String) : Option<Int> {
    data object SortByName : SortTypeOption(0, getString(R.string.by_name))
    data object SortByAppMtime : SortTypeOption(1, getString(R.string.by_update_time))
    data object SortByTriggerTime : SortTypeOption(2, getString(R.string.by_trigger_time))

    companion object {
        // https://stackoverflow.com/questions/47648689
        val allSubObject by lazy { arrayOf(SortByName, SortByAppMtime, SortByTriggerTime) }
    }
}

sealed class UpdateTimeOption(
    override val value: Long,
    override val label: String
) : Option<Long> {
    data object Pause : UpdateTimeOption(-1, getString(R.string.pause))
    data object Everyday : UpdateTimeOption(24 * 60 * 60_000, getString(R.string.every_day))
    data object Every3Days :
        UpdateTimeOption(24 * 60 * 60_000 * 3, getString(R.string.every_3_days))

    data object Every7Days :
        UpdateTimeOption(24 * 60 * 60_000 * 7, getString(R.string.every_7_days))

    companion object {
        val allSubObject by lazy { arrayOf(Pause, Everyday, Every3Days, Every7Days) }
    }
}

sealed class DarkThemeOption(
    override val value: Boolean?,
    override val label: String
) : Option<Boolean?> {
    data object FollowSystem : DarkThemeOption(null, getString(R.string.follow_system))
    data object AlwaysEnable : DarkThemeOption(true, getString(R.string.always_enabled))
    data object AlwaysDisable : DarkThemeOption(false, getString(R.string.always_disabled))

    companion object {
        val allSubObject by lazy { arrayOf(FollowSystem, AlwaysEnable, AlwaysDisable) }
    }
}

sealed class EnableGroupOption(
    override val value: Boolean?,
    override val label: String
) : Option<Boolean?> {
    data object FollowSubs : DarkThemeOption(null, getString(R.string.follow_subscribe))
    data object AllEnable : DarkThemeOption(true, getString(R.string.enable_all))
    data object AllDisable : DarkThemeOption(false, getString(R.string.disable_all))

    companion object {
        val allSubObject by lazy { arrayOf(FollowSubs, AllEnable, AllDisable) }
    }
}

sealed class RuleSortOption(override val value: Int, override val label: String) : Option<Int> {
    data object Default : RuleSortOption(0, getString(R.string.by_subscription))
    data object ByTime : RuleSortOption(1, getString(R.string.by_trigger_time))
    data object ByName : RuleSortOption(2, getString(R.string.by_name))

    companion object {
        val allSubObject by lazy { arrayOf(Default, ByTime, ByName) }
    }
}

sealed class UpdateChannelOption(
    override val value: Int,
    override val label: String
) : Option<Int> {
    abstract val url: String

    data object Stable : UpdateChannelOption(0, getString(R.string.stable_version)) {
        override val url = "https://registry.npmmirror.com/@gkd-kit/app/latest/files/index.json"
    }

    data object Beta : UpdateChannelOption(1, getString(R.string.beta_version)) {
        override val url =
            "https://registry.npmmirror.com/@gkd-kit/app-beta/latest/files/index.json"
    }

    companion object {
        val allSubObject by lazy { arrayOf(Stable, Beta) }
    }
}
