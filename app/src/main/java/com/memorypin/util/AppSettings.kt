package com.memorypin.util

import android.content.Context
import androidx.annotation.StringRes
import com.memorypin.R
import com.memorypin.data.DeclinedDevice
import com.memorypin.data.QuietPlace
import com.memorypin.data.QuietPlaceType
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 앱 전역 설정. (두고 옴 알림 사용 여부 + 알림 민감도 + 알림 제외 구역)
 * 내부 저장소 SharedPreferences에 보관한다.
 */
object AppSettings {

    /**
     * "두고 옴" 판정 거리. 끊긴 위치에서 이 거리 이상 멀어지면 알림.
     */
    enum class Sensitivity(
        val key: String,
        val meters: Int,
        @StringRes val labelRes: Int
    ) {
        NEAR("near", 100, R.string.sensitivity_near),
        NORMAL("normal", 150, R.string.sensitivity_normal),
        FAR("far", 250, R.string.sensitivity_far);

        companion object {
            fun fromKey(key: String?): Sensitivity =
                entries.firstOrNull { it.key == key } ?: NORMAL
        }
    }

    /** 알림 제외 구역 반경. 이 거리 안에서 끊기면 알림을 보내지 않는다. */
    const val QUIET_ZONE_RADIUS_METERS = 150f

    /** @deprecated Use [QUIET_ZONE_RADIUS_METERS] */
    const val HOME_RADIUS_METERS = QUIET_ZONE_RADIUS_METERS

    const val MAX_FAVORITE_PLACES = 5

    private const val PREFS = "memorypin_settings"
    private const val KEY_MONITORING = "monitoring_enabled"
    private const val KEY_LEFT_BEHIND = "left_behind_enabled"
    private const val KEY_AUTO_REGISTER = "auto_register_on_connect"
    private const val KEY_DECLINED_DEVICES = "declined_devices_json"
    private const val KEY_DECLINED_AUTO_REGISTER = "declined_auto_register"
    private const val KEY_SENSITIVITY = "sensitivity"
    private const val KEY_HOME_LAT = "home_lat"
    private const val KEY_HOME_LNG = "home_lng"
    private const val KEY_QUIET_PLACES = "quiet_places_json"

    private const val ID_HOME = "home"
    private const val ID_WORK = "work"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 홈의 자동 저장 on/off. 기본 true(기존 사용자 동작 유지). */
    fun isMonitoringEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MONITORING, true)

    fun setMonitoringEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_MONITORING, enabled).apply()
    }

    fun isLeftBehindEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LEFT_BEHIND, true)

    fun setLeftBehindEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_LEFT_BEHIND, enabled).apply()
    }

    fun isAutoRegisterOnConnectEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_REGISTER, true)

    fun setAutoRegisterOnConnectEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_REGISTER, enabled).apply()
    }

    fun getDeclinedDevices(context: Context): List<DeclinedDevice> =
        loadDeclinedDevices(context)

    /** 이전 전역 민감도 설정을 한 번 읽고 제거한다. (기기별 설정 마이그레이션용) */
    fun consumeLegacySensitivity(context: Context): Sensitivity? {
        val key = prefs(context).getString(KEY_SENSITIVITY, null) ?: return null
        prefs(context).edit().remove(KEY_SENSITIVITY).apply()
        return Sensitivity.fromKey(key)
    }

    fun isDeclinedForAutoRegister(context: Context, sourceId: String): Boolean =
        getDeclinedDevices(context).any { it.sourceId.equals(sourceId, ignoreCase = true) }

    fun addDeclinedForAutoRegister(
        context: Context,
        sourceId: String,
        displayName: String
    ) {
        val normalizedId = sourceId.uppercase()
        val list = loadDeclinedDevices(context)
            .filterNot { it.sourceId.equals(normalizedId, ignoreCase = true) }
            .toMutableList()
        list.add(DeclinedDevice(normalizedId, displayName))
        saveDeclinedDevices(context, list)
    }

    fun clearDeclinedForAutoRegister(context: Context, sourceId: String) {
        val list = loadDeclinedDevices(context)
        val filtered = list.filterNot { it.sourceId.equals(sourceId, ignoreCase = true) }
        if (filtered.size != list.size) {
            saveDeclinedDevices(context, filtered)
        }
    }

    private fun loadDeclinedDevices(context: Context): List<DeclinedDevice> {
        val p = prefs(context)
        val json = p.getString(KEY_DECLINED_DEVICES, null)
        if (!json.isNullOrBlank()) {
            return runCatching {
                val arr = JSONArray(json)
                (0 until arr.length()).mapNotNull { i ->
                    val obj = arr.getJSONObject(i)
                    val id = obj.optString("sourceId").ifBlank { return@mapNotNull null }
                    DeclinedDevice(
                        sourceId = id.uppercase(),
                        displayName = obj.optString("displayName", id)
                    )
                }
            }.getOrDefault(emptyList())
        }

        val legacy = p.getStringSet(KEY_DECLINED_AUTO_REGISTER, emptySet()) ?: emptySet()
        if (legacy.isEmpty()) return emptyList()

        val migrated = legacy.map { DeclinedDevice(it.uppercase(), it) }
        saveDeclinedDevices(context, migrated)
        p.edit().remove(KEY_DECLINED_AUTO_REGISTER).apply()
        return migrated
    }

    private fun saveDeclinedDevices(context: Context, devices: List<DeclinedDevice>) {
        val arr = JSONArray()
        devices.forEach { device ->
            arr.put(
                JSONObject()
                    .put("sourceId", device.sourceId.uppercase())
                    .put("displayName", device.displayName)
            )
        }
        prefs(context).edit().putString(KEY_DECLINED_DEVICES, arr.toString()).apply()
    }

    // ---- 알림 제외 구역 (집·회사·자주 가는 곳) ----

    fun getQuietPlaces(context: Context): List<QuietPlace> =
        loadQuietPlaces(context)

    fun getPlace(context: Context, type: QuietPlaceType): QuietPlace? =
        getQuietPlaces(context).firstOrNull { it.type == type && it.type != QuietPlaceType.FAVORITE }

    fun getFavoritePlaces(context: Context): List<QuietPlace> =
        getQuietPlaces(context).filter { it.type == QuietPlaceType.FAVORITE }

    fun hasHome(context: Context): Boolean =
        getQuietPlaces(context).any { it.type == QuietPlaceType.HOME }

    fun getHome(context: Context): Pair<Double, Double>? =
        getQuietPlaces(context).firstOrNull { it.type == QuietPlaceType.HOME }
            ?.let { it.latitude to it.longitude }

    fun setHome(
        context: Context,
        latitude: Double,
        longitude: Double,
        address: String? = null
    ) {
        upsertFixedPlace(
            context,
            QuietPlace(
                id = ID_HOME,
                type = QuietPlaceType.HOME,
                label = context.getString(R.string.settings_home_label),
                latitude = latitude,
                longitude = longitude,
                address = address
            )
        )
    }

    fun clearHome(context: Context) {
        removeQuietPlace(context, ID_HOME)
    }

    fun hasWork(context: Context): Boolean =
        getQuietPlaces(context).any { it.type == QuietPlaceType.WORK }

    fun getWork(context: Context): QuietPlace? =
        getQuietPlaces(context).firstOrNull { it.type == QuietPlaceType.WORK }

    fun setWork(
        context: Context,
        latitude: Double,
        longitude: Double,
        address: String? = null
    ) {
        upsertFixedPlace(
            context,
            QuietPlace(
                id = ID_WORK,
                type = QuietPlaceType.WORK,
                label = context.getString(R.string.settings_work_label),
                latitude = latitude,
                longitude = longitude,
                address = address
            )
        )
    }

    fun clearWork(context: Context) {
        removeQuietPlace(context, ID_WORK)
    }

    fun addFavoritePlace(
        context: Context,
        label: String,
        latitude: Double,
        longitude: Double,
        address: String? = null
    ): QuietPlace? {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return null
        val list = loadQuietPlaces(context).toMutableList()
        val favorites = list.count { it.type == QuietPlaceType.FAVORITE }
        if (favorites >= MAX_FAVORITE_PLACES) return null
        val place = QuietPlace(
            id = UUID.randomUUID().toString(),
            type = QuietPlaceType.FAVORITE,
            label = trimmed,
            latitude = latitude,
            longitude = longitude,
            address = address
        )
        list.add(place)
        saveQuietPlaces(context, list)
        return place
    }

    fun removeQuietPlace(context: Context, id: String) {
        val list = loadQuietPlaces(context)
        val filtered = list.filterNot { it.id == id }
        if (filtered.size != list.size) {
            saveQuietPlaces(context, filtered)
        }
    }

    fun updateQuietPlaceAddress(context: Context, id: String, address: String?) {
        val list = loadQuietPlaces(context).toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return
        list[idx] = list[idx].copy(address = address)
        saveQuietPlaces(context, list)
    }

    /** 현재 좌표가 등록된 알림 제외 구역 안인지 확인한다. */
    fun isInQuietZone(context: Context, latitude: Double, longitude: Double): Boolean {
        return getQuietPlaces(context).any { place ->
            GeoUtil.distanceMeters(
                place.latitude, place.longitude, latitude, longitude
            ) <= QUIET_ZONE_RADIUS_METERS
        }
    }

    /** @deprecated Use [isInQuietZone] */
    fun isAtHome(context: Context, latitude: Double, longitude: Double): Boolean =
        isInQuietZone(context, latitude, longitude)

    private fun upsertFixedPlace(context: Context, place: QuietPlace) {
        val list = loadQuietPlaces(context)
            .filterNot { it.id == place.id || it.type == place.type && place.type != QuietPlaceType.FAVORITE }
            .toMutableList()
        list.add(0, place)
        saveQuietPlaces(context, list)
    }

    private fun loadQuietPlaces(context: Context): List<QuietPlace> {
        val p = prefs(context)
        val json = p.getString(KEY_QUIET_PLACES, null)
        if (!json.isNullOrBlank()) {
            return runCatching {
                val arr = JSONArray(json)
                (0 until arr.length()).mapNotNull { i ->
                    val obj = arr.getJSONObject(i)
                    val id = obj.optString("id").ifBlank { return@mapNotNull null }
                    QuietPlace(
                        id = id,
                        type = QuietPlaceType.fromKey(obj.optString("type")),
                        label = obj.optString("label").ifBlank { id },
                        latitude = obj.getDouble("latitude"),
                        longitude = obj.getDouble("longitude"),
                        address = obj.optString("address").takeIf { it.isNotBlank() }
                    )
                }
            }.getOrDefault(emptyList())
        }

        // 이전 버전 집 좌표 마이그레이션
        val lat = p.getString(KEY_HOME_LAT, null)?.toDoubleOrNull()
        val lng = p.getString(KEY_HOME_LNG, null)?.toDoubleOrNull()
        if (lat == null || lng == null) return emptyList()

        val migrated = listOf(
            QuietPlace(
                id = ID_HOME,
                type = QuietPlaceType.HOME,
                label = context.getString(R.string.settings_home_label),
                latitude = lat,
                longitude = lng
            )
        )
        saveQuietPlaces(context, migrated)
        p.edit().remove(KEY_HOME_LAT).remove(KEY_HOME_LNG).apply()
        return migrated
    }

    private fun saveQuietPlaces(context: Context, places: List<QuietPlace>) {
        val arr = JSONArray()
        places.forEach { place ->
            arr.put(
                JSONObject()
                    .put("id", place.id)
                    .put("type", place.type.key)
                    .put("label", place.label)
                    .put("latitude", place.latitude)
                    .put("longitude", place.longitude)
                    .apply { place.address?.let { put("address", it) } }
            )
        }
        prefs(context).edit().putString(KEY_QUIET_PLACES, arr.toString()).apply()
    }
}
