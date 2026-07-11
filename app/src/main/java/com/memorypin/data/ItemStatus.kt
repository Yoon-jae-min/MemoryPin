package com.memorypin.data

/**
 * 추적 대상의 현재 상태.
 *
 * 연결/끊김 + 이동에 따라 상태 기계처럼 전이된다.
 *
 *   [PRESENT] ──끊김──> [SAVED] ──멀어짐──> [LEFT_BEHIND]
 *       ▲                                       │
 *       └──────────── 다시 연결(=찾음) ──────────┘
 */
enum class ItemStatus(val key: String) {
    /** 현재 연결되어 함께 있음. */
    PRESENT("present"),

    /** 연결이 끊겨 위치가 저장된 평범한 상태. */
    SAVED("saved"),

    /** 끊긴 뒤 멀어져서 "두고 옴"으로 판정되어 알림이 발송된 상태. */
    LEFT_BEHIND("left_behind");

    companion object {
        fun fromKey(key: String?): ItemStatus =
            entries.firstOrNull { it.key == key } ?: SAVED
    }
}
