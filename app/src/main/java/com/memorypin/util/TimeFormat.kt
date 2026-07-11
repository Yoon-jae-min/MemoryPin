package com.memorypin.util

import android.text.format.DateUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeFormat {

    /** "방금 전 / 5분 전 / 어제" 같은 상대 시간. */
    fun relative(millis: Long): CharSequence {
        if (millis <= 0L) return "-"
        return DateUtils.getRelativeTimeSpanString(
            millis,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        )
    }

    /** "2026.06.28 15:30" 형태의 전체 시간. */
    fun full(millis: Long): String {
        if (millis <= 0L) return "-"
        val fmt = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault())
        return fmt.format(Date(millis))
    }
}
