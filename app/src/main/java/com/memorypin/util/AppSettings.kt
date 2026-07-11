package com.memorypin.util

import android.content.Context
import androidx.annotation.StringRes
import com.memorypin.R
import com.memorypin.data.DeclinedDevice
import org.json.JSONArray
import org.json.JSONObject

/**
 * 앱 전역 설정. (두고 옴 알림 사용 여부 + 알림 민감도)
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

    /** 집 반경. 이 거리 안에서 끊기면 알림을 보내지 않는다. */
    const val HOME_RADIUS_METERS = 150f

    private const val PREFS = "memorypin_settings"
    private const val KEY_LEFT_BEHIND = "left_behind_enabled"
    private const val KEY_AUTO_REGISTER = "auto_register_on_connect"
    private const val KEY_DECLINED_DEVICES = "declined_devices_json"
    private const val KEY_DECLINED_AUTO_REGISTER = "declined_auto_register"
    private const val KEY_SENSITIVITY = "sensitivity"
    private const val KEY_HOME_LAT = "home_lat"
    private const val KEY_HOME_LNG = "home_lng"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

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

        // 이전 버전(StringSet) 데이터 마이그레이션
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

    fun getSensitivity(context: Context): Sensitivity =
        Sensitivity.fromKey(prefs(context).getString(KEY_SENSITIVITY, null))

    fun setSensitivity(context: Context, sensitivity: Sensitivity) {
        prefs(context).edit().putString(KEY_SENSITIVITY, sensitivity.key).apply()
    }

    // ---- 집 등록 ----

    fun hasHome(context: Context): Boolean {
        val p = prefs(context)
        return p.contains(KEY_HOME_LAT) && p.contains(KEY_HOME_LNG)
    }

    fun getHome(context: Context): Pair<Double, Double>? {
        if (!hasHome(context)) return null
        val p = prefs(context)
        val lat = p.getString(KEY_HOME_LAT, null)?.toDoubleOrNull() ?: return null
        val lng = p.getString(KEY_HOME_LNG, null)?.toDoubleOrNull() ?: return null
        return lat to lng
    }

    fun setHome(context: Context, latitude: Double, longitude: Double) {
        prefs(context).edit()
            .putString(KEY_HOME_LAT, latitude.toString())
            .putString(KEY_HOME_LNG, longitude.toString())
            .apply()
    }

    fun clearHome(context: Context) {
        prefs(context).edit()
            .remove(KEY_HOME_LAT)
            .remove(KEY_HOME_LNG)
            .apply()
    }

    /** 현재 좌표가 등록된 집 반경 안인지 확인한다. */
    fun isAtHome(context: Context, latitude: Double, longitude: Double): Boolean {
        val home = getHome(context) ?: return false
        val distance = GeoUtil.distanceMeters(
            home.first, home.second, latitude, longitude
        )
        return distance <= HOME_RADIUS_METERS
    }
}
