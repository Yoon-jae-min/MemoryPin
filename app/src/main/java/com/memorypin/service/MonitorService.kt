package com.memorypin.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.memorypin.connection.ConnectionEvent
import com.memorypin.connection.ConnectionEventListener
import com.memorypin.connection.ConnectionManager
import com.memorypin.connection.SourceType
import com.memorypin.data.ItemStatus
import com.memorypin.data.MemoryPinRepository
import com.memorypin.data.Spot
import com.memorypin.location.LocationProvider
import com.memorypin.util.AppSettings
import com.memorypin.util.BluetoothUtil
import com.memorypin.util.GeoUtil
import com.memorypin.util.Notifications
import com.memorypin.util.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 백그라운드에서 연결 상태를 감지하는 포그라운드 서비스.
 *
 * 흐름:
 *  - 끊김  → 위치 저장 (+ 집 밖이면 알림) → 멀어지면 "두고 옴" 알림
 *  - 연결  → (두고 옴/저장 상태였다면) 자동으로 "함께 있음(찾음)" 처리 + 감시 중단
 *
 * 연결 방식 자체는 [ConnectionManager] 가 담당하므로, 이 서비스는
 * "이벤트 → 상태 전이 → 알림" 흐름만 책임진다.
 */
class MonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var connectionManager: ConnectionManager
    private lateinit var locationProvider: LocationProvider

    /** itemId → "두고 옴" 감시 중인 코루틴 잡. */
    private val watchJobs = mutableMapOf<String, Job>()

    /** 같은 연결에서 추적 확인 알림이 반복되지 않도록 한다. */
    private val promptedAddresses = mutableSetOf<String>()

    private val listener = ConnectionEventListener { event ->
        when (event.state) {
            ConnectionEvent.State.DISCONNECTED -> handleDisconnect(event)
            ConnectionEvent.State.CONNECTED -> handleConnect(event)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        MemoryPinRepository.init(applicationContext)
        Notifications.ensureChannels(this)

        locationProvider = LocationProvider(applicationContext)
        connectionManager = ConnectionManager(applicationContext, listener).apply {
            registerDefaultMonitors()
        }

        startInForeground()
        connectionManager.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        connectionManager.stop()
        scope.cancel()
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startInForeground() {
        val notification = Notifications.buildServiceNotification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                Notifications.SERVICE_NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(Notifications.SERVICE_NOTIF_ID, notification)
        }
    }

    // ---- 끊김: 위치 저장 + 두고 옴 감시 시작 ----

    private fun handleDisconnect(event: ConnectionEvent) {
        val tracked = MemoryPinRepository.findBySource(event.type, event.sourceId)
        if (tracked == null || !tracked.enabled) return
        if (!PermissionHelper.hasFineLocation(this)) return

        scope.launch {
            val location = locationProvider.getCurrent() ?: return@launch
            val inQuietZone = AppSettings.isInQuietZone(
                applicationContext, location.latitude, location.longitude
            )

            val spot = Spot(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = location.accuracy,
                savedAt = System.currentTimeMillis()
            )
            val saved = MemoryPinRepository.saveSpotForSource(event.type, event.sourceId, spot)
                ?: return@launch

            // 집·회사·자주 가는 곳에서는 위치만 조용히 저장하고 알림은 보내지 않는다.
            if (inQuietZone) return@launch

            Notifications.showSavedNotification(applicationContext, saved)

            if (AppSettings.isLeftBehindEnabled(applicationContext) && saved.alertEnabled) {
                startLeftBehindWatch(saved.id, spot)
            }
        }
    }

    /**
     * 끊긴 자리에서 일정 시간(지연) 뒤부터 주기적으로 위치를 확인해,
     * 저장된 지점에서 민감도 거리 이상 멀어지면 "두고 옴" 알림을 띄운다.
     * 그 사이 기기가 다시 연결되면 [handleConnect] 가 이 잡을 취소한다.
     */
    private fun startLeftBehindWatch(itemId: String, origin: Spot) {
        watchJobs.remove(itemId)?.cancel()
        watchJobs[itemId] = scope.launch {
            delay(DISCONNECT_DELAY_MS)
            val deadline = System.currentTimeMillis() + MAX_WATCH_MS

            while (isActive && System.currentTimeMillis() < deadline) {
                val item = MemoryPinRepository.getById(itemId)
                if (item == null || item.carryingWhileDisconnected) break

                val thresholdMeters = item.sensitivity.meters
                val current = locationProvider.getCurrent()
                if (current != null) {
                    val distance = GeoUtil.distanceMeters(
                        origin.latitude, origin.longitude,
                        current.latitude, current.longitude
                    )
                    if (distance >= thresholdMeters) {
                        val latest = MemoryPinRepository.getById(itemId)
                        if (latest != null &&
                            latest.enabled &&
                            latest.alertEnabled &&
                            !latest.carryingWhileDisconnected &&
                            latest.status != ItemStatus.PRESENT
                        ) {
                            MemoryPinRepository.setStatus(itemId, ItemStatus.LEFT_BEHIND)
                            Notifications.showLeftBehindNotification(applicationContext, latest)
                        }
                        break
                    }
                }
                delay(WATCH_INTERVAL_MS)
            }
            watchJobs.remove(itemId)
        }
    }

    // ---- 연결: 자동 "찾음" 처리 ----

    private fun handleConnect(event: ConnectionEvent) {
        val tracked = MemoryPinRepository.findBySource(event.type, event.sourceId)
        if (tracked != null) {
            watchJobs.remove(tracked.id)?.cancel()
            if (tracked.carryingWhileDisconnected) {
                MemoryPinRepository.setCarryingWhileDisconnected(tracked.id, false)
            }
            if (tracked.status != ItemStatus.PRESENT) {
                MemoryPinRepository.setStatus(tracked.id, ItemStatus.PRESENT)
            }
            return
        }
        maybePromptAutoRegister(event)
    }

    private fun maybePromptAutoRegister(event: ConnectionEvent) {
        if (event.type != SourceType.BLUETOOTH) return
        val ctx = applicationContext
        if (!AppSettings.isAutoRegisterOnConnectEnabled(ctx)) return
        if (AppSettings.isDeclinedForAutoRegister(ctx, event.sourceId)) return
        if (!BluetoothUtil.isBonded(ctx, event.sourceId)) return
        if (!promptedAddresses.add(event.sourceId.uppercase())) return

        Notifications.showTrackPromptNotification(
            ctx,
            event.type,
            event.sourceId,
            event.label
        )
    }

    private fun cancelWatch(itemId: String) {
        watchJobs.remove(itemId)?.cancel()
    }

    companion object {
        @Volatile
        private var instance: MonitorService? = null

        /** 알림 버튼(함께 이동 중)으로 두고 옴 감시를 중단할 때 호출. */
        fun cancelLeftBehindWatch(itemId: String) {
            instance?.cancelWatch(itemId)
        }
        /** 끊긴 직후 잠깐 멀어진 것(오알림)을 거르는 지연 시간. */
        private const val DISCONNECT_DELAY_MS = 60_000L

        /** 멀어졌는지 다시 확인하는 주기. */
        private const val WATCH_INTERVAL_MS = 25_000L

        /** 배터리 보호를 위해 감시를 멈추는 최대 시간. */
        private const val MAX_WATCH_MS = 10 * 60_000L

        fun start(context: Context) {
            if (!PermissionHelper.canMonitor(context)) return
            val intent = Intent(context, MonitorService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitorService::class.java))
        }
    }
}
