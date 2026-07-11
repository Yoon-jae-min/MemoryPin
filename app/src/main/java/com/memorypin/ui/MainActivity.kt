package com.memorypin.ui

import android.Manifest
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.memorypin.R
import com.memorypin.data.MemoryPinRepository
import com.memorypin.data.TrackedItem
import com.memorypin.databinding.ActivityMainBinding
import com.memorypin.service.MonitorService
import com.memorypin.util.AppSettings
import com.memorypin.util.PermissionHelper
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val adapter = TrackedItemAdapter { openDetail(it) }

    private var hasItems = false

    private val requestCoreLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { maybeRequestBackgroundLocation() }

    private val requestBackgroundLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { startServiceIfReady() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        MobileAds.initialize(this) {}
        binding.adViewBanner.loadAd(AdRequest.Builder().build())

        binding.recyclerItems.layoutManager = LinearLayoutManager(this)
        binding.recyclerItems.adapter = adapter

        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, AddItemActivity::class.java))
        }
        binding.statusCard.setOnClickListener {
            ensurePermissionsForMonitoring(force = true)
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        observeItems()
    }

    override fun onResume() {
        super.onResume()
        binding.adViewBanner.resume()
        updateMonitoringStatus()
        startServiceIfReady()
    }

    override fun onPause() {
        binding.adViewBanner.pause()
        super.onPause()
    }

    override fun onDestroy() {
        binding.adViewBanner.destroy()
        super.onDestroy()
    }

    private fun observeItems() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                MemoryPinRepository.items.collect { items ->
                    hasItems = items.isNotEmpty()
                    adapter.submitList(items)
                    binding.emptyState.visibility = if (hasItems) View.GONE else View.VISIBLE
                    binding.recyclerItems.visibility = if (hasItems) View.VISIBLE else View.GONE
                    binding.sectionLabel.visibility = if (hasItems) View.VISIBLE else View.GONE
                    binding.tvDeviceCount.text = getString(R.string.home_device_count, items.size)
                    updateMonitoringStatus()

                    if (shouldRunMonitor() && !PermissionHelper.canMonitor(this@MainActivity)) {
                        ensurePermissionsForMonitoring(force = false)
                    } else {
                        startServiceIfReady()
                    }
                }
            }
        }
    }

    private fun openDetail(item: TrackedItem) {
        startActivity(
            Intent(this, SpotDetailActivity::class.java)
                .putExtra(SpotDetailActivity.EXTRA_ITEM_ID, item.id)
        )
    }

    private fun updateMonitoringStatus() {
        val on = shouldRunMonitor()
        binding.tvMonitoringStatus.text = getString(
            if (on) R.string.home_monitoring_on else R.string.home_monitoring_off
        )
        binding.tvMonitoringDesc.text = getString(
            when {
                !on -> R.string.home_monitoring_off_desc
                hasItems -> R.string.home_monitoring_on_desc
                else -> R.string.home_monitoring_on_auto_register_desc
            }
        )
        binding.imgStatusIcon.setImageResource(
            if (on) R.drawable.ic_shield_check else R.drawable.ic_pause_circle
        )
        val statusColor = ContextCompat.getColor(
            this, if (on) R.color.success else R.color.on_surface_variant
        )
        binding.statusIconHolder.backgroundTintList = ColorStateList.valueOf(statusColor)
    }

    // ---- 권한 ----

    private fun ensurePermissionsForMonitoring(force: Boolean) {
        val needed = mutableListOf<String>()
        if (!PermissionHelper.hasFineLocation(this)) {
            needed += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !PermissionHelper.hasBluetoothConnect(this)
        ) {
            needed += Manifest.permission.BLUETOOTH_CONNECT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !PermissionHelper.hasNotifications(this)
        ) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }

        if (needed.isNotEmpty()) {
            if (force || !wasAsked()) {
                markAsked()
                requestCoreLauncher.launch(needed.toTypedArray())
            }
        } else {
            maybeRequestBackgroundLocation()
        }
    }

    private fun maybeRequestBackgroundLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            PermissionHelper.hasFineLocation(this) &&
            !PermissionHelper.hasBackgroundLocation(this)
        ) {
            AlertDialog.Builder(this)
                .setTitle(R.string.perm_title)
                .setMessage(R.string.perm_background_rationale)
                .setPositiveButton(R.string.perm_allow) { _, _ ->
                    requestBackgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
                .setNegativeButton(R.string.perm_later) { _, _ -> startServiceIfReady() }
                .show()
        } else {
            startServiceIfReady()
        }
    }

    private fun startServiceIfReady() {
        if (shouldRunMonitor()) {
            MonitorService.start(this)
        }
        updateMonitoringStatus()
    }

    private fun shouldRunMonitor(): Boolean =
        PermissionHelper.canMonitor(this) &&
            (hasItems || AppSettings.isAutoRegisterOnConnectEnabled(this))

    private fun wasAsked(): Boolean =
        getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_ASKED, false)

    private fun markAsked() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean(KEY_ASKED, true).apply()
    }

    companion object {
        private const val PREFS = "memorypin_prefs"
        private const val KEY_ASKED = "asked_permissions"
    }
}
