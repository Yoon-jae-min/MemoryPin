package com.memorypin.util

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.os.Build

object BluetoothUtil {

    fun isBonded(context: Context, address: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !PermissionHelper.hasBluetoothConnect(context)
        ) {
            return false
        }
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
        return try {
            @Suppress("MissingPermission")
            adapter.bondedDevices?.any { it.address.equals(address, ignoreCase = true) } == true
        } catch (_: SecurityException) {
            false
        }
    }
}
