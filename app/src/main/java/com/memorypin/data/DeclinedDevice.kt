package com.memorypin.data

/** 자동 등록 알림에서 "안 함"을 선택한 기기. */
data class DeclinedDevice(
    val sourceId: String,
    val displayName: String
)
