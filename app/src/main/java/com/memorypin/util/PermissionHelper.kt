package com.memorypin.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * 권한 확인 헬퍼. 버전별로 필요 없는 권한은 true로 처리한다.
 */
object PermissionHelper {

    fun hasFineLocation(context: Context): Boolean =
        granted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun hasBackgroundLocation(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }

    fun hasBluetoothConnect(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return granted(context, Manifest.permission.BLUETOOTH_CONNECT)
    }

    fun hasNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return granted(context, Manifest.permission.POST_NOTIFICATIONS)
    }

    /** 자동 저장이 동작하기 위한 최소 권한(위치 + 블루투스). */
    fun canMonitor(context: Context): Boolean =
        hasFineLocation(context) && hasBluetoothConnect(context)

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
