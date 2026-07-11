package com.memorypin.data

import com.memorypin.connection.SourceType

/**
 * 사용자가 "위치 자동 저장"을 켜 둔 대상(기기).
 *
 * @param sourceType   연결 방식 (블루투스 등)
 * @param sourceId     연결 방식 안에서의 식별자 (BT MAC 주소 등)
 * @param enabled      자동 저장 사용 여부
 * @param alertEnabled              "두고 옴" 알림 사용 여부 (기기별)
 * @param carryingWhileDisconnected 끊긴 채로 함께 이동 중 (재연결 전까지 두고 옴 알림 억제)
 * @param status                    현재 상태 (연결됨 / 저장됨 / 두고 옴)
 * @param lastSpot                  가장 최근에 저장된 위치 (없으면 null)
 */
data class TrackedItem(
    val id: String,
    val sourceType: SourceType,
    val sourceId: String,
    val displayName: String,
    val iconKey: String,
    val enabled: Boolean = true,
    val alertEnabled: Boolean = true,
    val carryingWhileDisconnected: Boolean = false,
    val status: ItemStatus = ItemStatus.PRESENT,
    val createdAt: Long,
    val lastSpot: Spot? = null
)
