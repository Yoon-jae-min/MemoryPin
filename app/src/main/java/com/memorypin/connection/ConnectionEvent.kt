package com.memorypin.connection

/**
 * 연결 방식에 상관없이 통일된 형태로 전달되는 연결 상태 변화 이벤트.
 *
 * @param type      어떤 연결 방식인지 (블루투스 / 와이파이 등)
 * @param sourceId  연결 방식 안에서 기기를 식별하는 값 (BT MAC 주소, 와이파이 SSID 등)
 * @param label     사람이 읽을 수 있는 이름 (기기 이름 등). 없으면 sourceId를 그대로 사용
 * @param state     연결됨 / 끊김
 */
data class ConnectionEvent(
    val type: SourceType,
    val sourceId: String,
    val label: String,
    val state: State
) {
    enum class State { CONNECTED, DISCONNECTED }
}

/** 연결 방식 구현체가 이벤트를 알릴 때 사용하는 콜백. */
fun interface ConnectionEventListener {
    fun onEvent(event: ConnectionEvent)
}
