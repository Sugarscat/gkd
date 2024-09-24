package com.sugarscat.jump.util

import com.blankj.utilcode.util.StringUtils.getString
import com.sugarscat.jump.R
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

fun formatTimeAgo(timestamp: Long): String {
    val currentTime = System.currentTimeMillis()
    val timeDifference = currentTime - timestamp

    val minutes = TimeUnit.MILLISECONDS.toMinutes(timeDifference)
    val hours = TimeUnit.MILLISECONDS.toHours(timeDifference)
    val days = TimeUnit.MILLISECONDS.toDays(timeDifference)
    val weeks = days / 7
    val months = (days / 30)
    val years = (days / 365)
    return when {
        years > 0 -> getString(R.string.years_ago, years)
        months > 0 -> getString(R.string.months_ago, months)
        weeks > 0 -> getString(R.string.weeks_ago, weeks)
        days > 0 -> getString(R.string.days_ago, days)
        hours > 0 -> getString(R.string.hours_ago, hours)
        minutes > 0 -> getString(R.string.minutes_ago, minutes)
        else -> getString(R.string.just)
    }
}

private val formatDateMap by lazy { hashMapOf<String, SimpleDateFormat>() }

fun Long.format(formatStr: String): String {
    var df = formatDateMap[formatStr]
    if (df == null) {
        df = SimpleDateFormat(formatStr, Locale.getDefault())
        formatDateMap[formatStr] = df
    }
    return df.format(this)
}

data class ThrottleTimer(
    private val interval: Long = 500L,
    private var value: Long = 0L
) {
    fun expired(): Boolean {
        val t = System.currentTimeMillis()
        if (t - value > interval) {
            value = t
            return true
        }
        return false
    }
}

private val defaultThrottleTimer by lazy { ThrottleTimer() }

fun throttle(
    timer: ThrottleTimer = defaultThrottleTimer,
    fn: (() -> Unit),
): (() -> Unit) {
    return {
        if (timer.expired()) {
            fn.invoke()
        }
    }
}

fun <T> throttle(
    timer: ThrottleTimer = defaultThrottleTimer,
    fn: ((T) -> Unit),
): ((T) -> Unit) {
    return {
        if (timer.expired()) {
            fn.invoke(it)
        }
    }
}
