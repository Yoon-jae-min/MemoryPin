package com.memorypin.data

/**
 * 두고 옴 알림을 보내지 않는 구역(집·회사·자주 가는 곳).
 */
data class QuietPlace(
    val id: String,
    val type: QuietPlaceType,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val address: String? = null
)

enum class QuietPlaceType(val key: String) {
    HOME("home"),
    WORK("work"),
    FAVORITE("favorite");

    companion object {
        fun fromKey(key: String?): QuietPlaceType =
            entries.firstOrNull { it.key == key } ?: FAVORITE
    }
}
