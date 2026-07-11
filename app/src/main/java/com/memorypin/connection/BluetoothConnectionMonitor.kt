package com.memorypin.connection

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * 블루투스 기기의 연결/끊김을 감시하는 [ConnectionMonitor] 구현체.
 *
 * 시스템이 보내는 ACL_CONNECTED / ACL_DISCONNECTED 브로드캐스트를 수신해
 * [ConnectionEvent] 로 변환한 뒤 리스너에 전달한다.
 */
class BluetoothConnectionMonitor(
    private val context: Context,
    private val listener: ConnectionEventListener
) : ConnectionMonitor {

    override val type: SourceType = SourceType.BLUETOOTH

    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val device = intent.deviceExtra() ?: return
            val sourceId = device.address ?: return
            val label = device.safeName(ctx) ?: sourceId

            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> dispatch(
                    sourceId, label, ConnectionEvent.State.CONNECTED
                )

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> dispatch(
                    sourceId, label, ConnectionEvent.State.DISCONNECTED
                )
            }
        }
    }

    override fun start() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
        registered = true
    }

    override fun stop() {
        if (!registered) return
        runCatching { context.unregisterReceiver(receiver) }
        registered = false
    }

    private fun dispatch(sourceId: String, label: String, state: ConnectionEvent.State) {
        listener.onEvent(ConnectionEvent(SourceType.BLUETOOTH, sourceId, label, state))
    }

    private fun Intent.deviceExtra(): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    private fun BluetoothDevice.safeName(ctx: Context): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val granted = ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return null
        }
        return try {
            @Suppress("MissingPermission")
            name
        } catch (e: SecurityException) {
            null
        }
    }
}
