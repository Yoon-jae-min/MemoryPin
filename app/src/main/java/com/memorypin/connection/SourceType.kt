package com.memorypin.connection

/**
 * 위치를 저장하는 트리거가 되는 "연결 방식"의 종류.
 *
 * 새로운 연결 방식을 추가하려면:
 *  1) 여기에 항목을 추가하고
 *  2) [ConnectionMonitor] 를 구현한 클래스를 만든 뒤
 *  3) [ConnectionManager.registerDefaultMonitors] 에 등록하면 됩니다.
 */
enum class SourceType(val key: String) {
    BLUETOOTH("bluetooth"),

    // 향후 확장 예정 (구현체가 준비되면 ConnectionManager에 등록)
    WIFI("wifi"),
    BLE_TAG("ble_tag");

    companion object {
        fun fromKey(key: String?): SourceType =
            entries.firstOrNull { it.key == key } ?: BLUETOOTH
    }
}
