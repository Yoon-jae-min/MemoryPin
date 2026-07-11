package com.memorypin.data

/**
 * 연결이 끊긴 순간 저장된 위치 한 건.
 */
data class Spot(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val savedAt: Long,
    val memo: String? = null
)
