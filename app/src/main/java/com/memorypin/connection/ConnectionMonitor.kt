package com.memorypin.connection

/**
 * "연결 방식" 한 가지를 감시하는 컴포넌트의 공통 인터페이스.
 *
 * 블루투스, 와이파이, BLE 태그 등 어떤 방식이든 이 인터페이스만 구현하면
 * [ConnectionManager] 에 동일하게 끼워 넣을 수 있다.
 */
interface ConnectionMonitor {

    /** 이 모니터가 담당하는 연결 방식. */
    val type: SourceType

    /** 감시를 시작한다. (브로드캐스트 등록 등) */
    fun start()

    /** 감시를 중단하고 리소스를 정리한다. */
    fun stop()
}
