package com.memorypin.util

import android.location.Location

object GeoUtil {

    /** 두 좌표 사이 거리(미터). */
    fun distanceMeters(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Float {
        val result = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, result)
        return result[0]
    }
}
