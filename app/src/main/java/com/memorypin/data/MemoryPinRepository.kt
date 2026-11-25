package com.memorypin.data

import android.content.Context
import com.memorypin.connection.SourceType
import com.memorypin.util.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * 추적 대상과 저장된 위치를 보관하는 단일 저장소.
 *
 * 데이터 양이 적어 외부 DB 없이 내부 저장소의 JSON 파일에 보관한다.
 * 코드 생성(annotation processor)이 없어 빌드가 단순하다.
 */
object MemoryPinRepository {

    private const val FILE_NAME = "tracked_items.json"

    private lateinit var appContext: Context
    private val lock = Any()

    private val _items = MutableStateFlow<List<TrackedItem>>(emptyList())
    val items: StateFlow<List<TrackedItem>> = _items.asStateFlow()

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        var list = load()
        val legacySensitivity = AppSettings.consumeLegacySensitivity(context)
        if (legacySensitivity != null) {
            list = list.map { it.copy(sensitivity = legacySensitivity) }
            persist(list)
        }
        _items.value = list
    }

    // ---- 조회 ----

    fun getById(id: String): TrackedItem? = _items.value.firstOrNull { it.id == id }

    fun findBySource(type: SourceType, sourceId: String): TrackedItem? =
        _items.value.firstOrNull { it.sourceType == type && it.sourceId.equals(sourceId, ignoreCase = true) }

    fun isTracked(type: SourceType, sourceId: String): Boolean =
        findBySource(type, sourceId) != null

    // ---- 변경 ----

    fun addItem(
        sourceType: SourceType,
        sourceId: String,
        displayName: String,
        iconKey: String
    ): TrackedItem {
        val existing = findBySource(sourceType, sourceId)
        if (existing != null) {
            val updated = existing.copy(
                enabled = true,
                // 사용자가 별명을 바꾼 경우 덮어쓰지 않는다.
                displayName = if (existing.hasCustomName) existing.displayName else displayName,
                originalName = displayName,
                iconKey = iconKey
            )
            replace(updated)
            return updated
        }
        val item = TrackedItem(
            id = UUID.randomUUID().toString(),
            sourceType = sourceType,
            sourceId = sourceId,
            displayName = displayName,
            originalName = displayName,
            iconKey = iconKey,
            enabled = true,
            alertEnabled = true,
            status = ItemStatus.PRESENT,
            createdAt = System.currentTimeMillis(),
            lastSpot = null
        )
        mutate { it + item }
        return item
    }

    fun update(item: TrackedItem) = replace(item)

    fun setDisplayName(id: String, displayName: String) {
        val trimmed = displayName.trim()
        if (trimmed.isEmpty()) return
        getById(id)?.let {
            if (it.displayName != trimmed) replace(it.copy(displayName = trimmed))
        }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        getById(id)?.let { replace(it.copy(enabled = enabled)) }
    }

    fun setAlertEnabled(id: String, alertEnabled: Boolean) {
        getById(id)?.let { replace(it.copy(alertEnabled = alertEnabled)) }
    }

    fun setSensitivity(id: String, sensitivity: AppSettings.Sensitivity) {
        getById(id)?.let {
            if (it.sensitivity != sensitivity) replace(it.copy(sensitivity = sensitivity))
        }
    }

    fun setStatus(id: String, status: ItemStatus) {
        getById(id)?.let {
            if (it.status != status) replace(it.copy(status = status))
        }
    }

    /** 끊긴 채 함께 이동 중 표시. true면 재연결 전까지 두고 옴 알림을 보내지 않는다. */
    fun setCarryingWhileDisconnected(id: String, carrying: Boolean) {
        getById(id)?.let {
            if (it.carryingWhileDisconnected != carrying) {
                replace(it.copy(carryingWhileDisconnected = carrying))
            }
        }
    }

    fun updateMemo(id: String, memo: String?) {
        val item = getById(id) ?: return
        val spot = item.lastSpot ?: return
        replace(item.copy(lastSpot = spot.copy(memo = memo)))
    }

    fun remove(id: String) {
        mutate { list -> list.filterNot { it.id == id } }
    }

    /**
     * 연결 끊김 이벤트로 들어온 위치를 저장한다.
     * 해당 source가 추적 대상이고 활성화되어 있을 때만 저장하며,
     * 저장된 [TrackedItem] 을 반환한다. (대상이 아니면 null)
     */
    fun saveSpotForSource(type: SourceType, sourceId: String, spot: Spot): TrackedItem? {
        val item = findBySource(type, sourceId) ?: return null
        if (!item.enabled) return null
        val updated = item.copy(
            lastSpot = spot,
            status = ItemStatus.SAVED,
            carryingWhileDisconnected = false
        )
        replace(updated)
        return updated
    }

    fun saveSpotForId(id: String, spot: Spot): TrackedItem? {
        val item = getById(id) ?: return null
        val updated = item.copy(lastSpot = spot, status = ItemStatus.SAVED)
        replace(updated)
        return updated
    }

    // ---- 내부 ----

    private fun replace(item: TrackedItem) {
        mutate { list ->
            val idx = list.indexOfFirst { it.id == item.id }
            if (idx >= 0) list.toMutableList().also { it[idx] = item }
            else list + item
        }
    }

    private fun mutate(transform: (List<TrackedItem>) -> List<TrackedItem>) {
        synchronized(lock) {
            val newList = transform(_items.value)
            _items.value = newList
            persist(newList)
        }
    }

    private fun file(): File = File(appContext.filesDir, FILE_NAME)

    private fun persist(list: List<TrackedItem>) {
        runCatching {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            file().writeText(arr.toString())
        }
    }

    private fun load(): List<TrackedItem> {
        return runCatching {
            val f = file()
            if (!f.exists()) return emptyList()
            val arr = JSONArray(f.readText())
            (0 until arr.length()).mapNotNull { i ->
                runCatching { arr.getJSONObject(i).toTrackedItem() }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    private fun TrackedItem.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("sourceType", sourceType.key)
        put("sourceId", sourceId)
        put("displayName", displayName)
        put("originalName", originalName)
        put("iconKey", iconKey)
        put("enabled", enabled)
        put("alertEnabled", alertEnabled)
        put("sensitivity", sensitivity.key)
        put("carryingWhileDisconnected", carryingWhileDisconnected)
        put("status", status.key)
        put("createdAt", createdAt)
        lastSpot?.let { put("lastSpot", it.toJson()) }
    }

    private fun Spot.toJson(): JSONObject = JSONObject().apply {
        put("latitude", latitude)
        put("longitude", longitude)
        put("accuracy", accuracy.toDouble())
        put("savedAt", savedAt)
        memo?.let { put("memo", it) }
    }

    private fun JSONObject.toTrackedItem(): TrackedItem {
        val name = optString("displayName")
        return TrackedItem(
            id = getString("id"),
            sourceType = SourceType.fromKey(optString("sourceType")),
            sourceId = getString("sourceId"),
            displayName = name,
            originalName = optString("originalName").ifBlank { name },
            iconKey = optString("iconKey", "other"),
            enabled = optBoolean("enabled", true),
            alertEnabled = optBoolean("alertEnabled", true),
            sensitivity = AppSettings.Sensitivity.fromKey(
                optString("sensitivity").ifBlank { null }
            ),
            carryingWhileDisconnected = optBoolean("carryingWhileDisconnected", false),
            status = ItemStatus.fromKey(optString("status")),
            createdAt = optLong("createdAt", System.currentTimeMillis()),
            lastSpot = optJSONObject("lastSpot")?.toSpot()
        )
    }

    private fun JSONObject.toSpot(): Spot = Spot(
        latitude = getDouble("latitude"),
        longitude = getDouble("longitude"),
        accuracy = optDouble("accuracy", 0.0).toFloat(),
        savedAt = optLong("savedAt", 0L),
        memo = if (has("memo")) optString("memo") else null
    )
}
