package com.memorypin.data

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.memorypin.R

/**
 * 기기 카드에 보여줄 아이콘/색상 프리셋.
 */
enum class ItemIcon(
    val key: String,
    @DrawableRes val drawableRes: Int,
    @ColorRes val colorRes: Int,
    @StringRes val labelRes: Int
) {
    CAR("car", R.drawable.ic_car, R.color.type_car, R.string.icon_car),
    EARBUDS("earbuds", R.drawable.ic_earbuds, R.color.type_earbuds, R.string.icon_earbuds),
    SPEAKER("speaker", R.drawable.ic_speaker, R.color.type_speaker, R.string.icon_speaker),
    WATCH("watch", R.drawable.ic_watch, R.color.type_watch, R.string.icon_watch),
    TAG("tag", R.drawable.ic_tag, R.color.type_tag, R.string.icon_tag),
    OTHER("other", R.drawable.ic_device_other, R.color.type_other, R.string.icon_other);

    companion object {
        fun fromKey(key: String?): ItemIcon =
            entries.firstOrNull { it.key == key } ?: OTHER

        /** 기기 이름을 보고 아이콘을 추측한다. */
        fun guessFromName(name: String): ItemIcon {
            val n = name.lowercase()
            return when {
                listOf("car", "차", "vehicle", "auto", "기아", "현대", "bmw", "benz", "carlife", "android auto").any { n.contains(it) } -> CAR
                listOf("buds", "earbuds", "이어", "에어팟", "airpod", "pods", "headphone", "headset", "tws").any { n.contains(it) } -> EARBUDS
                listOf("speaker", "스피커", "sound", "boom", "soundbar", "audio").any { n.contains(it) } -> SPEAKER
                listOf("watch", "워치", "band", "밴드", "gear", "galaxy fit").any { n.contains(it) } -> WATCH
                listOf("tag", "태그", "tile", "smarttag").any { n.contains(it) } -> TAG
                else -> OTHER
            }
        }
    }
}
