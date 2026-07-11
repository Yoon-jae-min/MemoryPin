package com.memorypin.connection

import android.content.Context

/**
 * 여러 [ConnectionMonitor] 를 한데 모아 일괄 시작/중단하는 관리자.
 *
 * 새 연결 방식 추가 = 새 [ConnectionMonitor] 구현체를 만들어
 * [registerDefaultMonitors] 에 한 줄 추가하는 것으로 끝난다.
 */
class ConnectionManager(
    private val context: Context,
    private val listener: ConnectionEventListener
) {

    private val monitors = mutableListOf<ConnectionMonitor>()
    private var started = false

    /** 현재 지원하는 연결 방식의 모니터들을 등록한다. */
    fun registerDefaultMonitors() {
        addMonitorIfAbsent(BluetoothConnectionMonitor(context, listener))

        // 향후 확장 지점:
        // addMonitorIfAbsent(WifiConnectionMonitor(context, listener))
        // addMonitorIfAbsent(BleTagConnectionMonitor(context, listener))
    }

    /** 외부에서 직접 모니터를 추가할 수도 있다. */
    fun addMonitor(monitor: ConnectionMonitor) {
        addMonitorIfAbsent(monitor)
        if (started) monitor.start()
    }

    private fun addMonitorIfAbsent(monitor: ConnectionMonitor) {
        if (monitors.none { it.type == monitor.type }) {
            monitors += monitor
        }
    }

    fun start() {
        if (started) return
        monitors.forEach { it.start() }
        started = true
    }

    fun stop() {
        if (!started) return
        monitors.forEach { it.stop() }
        started = false
    }
}
