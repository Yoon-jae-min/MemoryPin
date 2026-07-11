package com.memorypin.ui

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.memorypin.R
import com.memorypin.connection.SourceType
import com.memorypin.data.ItemIcon
import com.memorypin.data.MemoryPinRepository
import com.memorypin.databinding.ActivityAddItemBinding
import com.memorypin.util.AppSettings
import com.memorypin.util.PermissionHelper

class AddItemActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddItemBinding
    private val adapter = PairedDeviceAdapter { addDevice(it) }

    private val requestBtLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddItemBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recyclerDevices.layoutManager = LinearLayoutManager(this)
        binding.recyclerDevices.adapter = adapter

        binding.btnBack.setOnClickListener { finish() }
        binding.btnOpenBtSettings.setOnClickListener {
            runCatching { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
        }

        ensureBluetoothPermission()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun ensureBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !PermissionHelper.hasBluetoothConnect(this)
        ) {
            requestBtLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    private fun refresh() {
        val devices = loadPairedDevices()
        adapter.submitList(devices)

        val empty = devices.isEmpty()
        binding.emptyState.visibility = if (empty) View.VISIBLE else View.GONE
        binding.recyclerDevices.visibility = if (empty) View.GONE else View.VISIBLE

        if (empty) {
            val needPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !PermissionHelper.hasBluetoothConnect(this)
            binding.tvEmpty.text = getString(
                if (needPermission) R.string.add_need_bluetooth_permission
                else R.string.add_bluetooth_empty
            )
        }
    }

    private fun loadPairedDevices(): List<PairedDevice> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !PermissionHelper.hasBluetoothConnect(this)
        ) {
            return emptyList()
        }

        val manager = getSystemService(BluetoothManager::class.java)
        val btAdapter = manager?.adapter ?: return emptyList()

        return try {
            @Suppress("MissingPermission")
            btAdapter.bondedDevices
                ?.map { device ->
                    val address = device.address
                    @Suppress("MissingPermission")
                    val name = device.name ?: address
                    PairedDevice(
                        name = name,
                        address = address,
                        added = MemoryPinRepository.isTracked(SourceType.BLUETOOTH, address)
                    )
                }
                ?.sortedBy { it.name.lowercase() }
                ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    private fun addDevice(device: PairedDevice) {
        val icon = ItemIcon.guessFromName(device.name)
        AppSettings.clearDeclinedForAutoRegister(this, device.address)
        MemoryPinRepository.addItem(
            sourceType = SourceType.BLUETOOTH,
            sourceId = device.address,
            displayName = device.name,
            iconKey = icon.key
        )
        Toast.makeText(this, device.name, Toast.LENGTH_SHORT).show()
        refresh()
    }
}
