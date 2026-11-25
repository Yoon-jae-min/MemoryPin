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
import com.memorypin.data.ItemStatus
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
    /** 백그라운드 위치 안내 동의 후, 같은 흐름에서 시스템 권한을 요청할 때 사용 */
    private var backgroundDisclosureAccepted = false

    private val requestCoreLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { requestBackgroundLocationIfNeeded() }

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
            toggleMonitoring()
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
                    adapter.submitList(sortHomeItems(items))
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

    /**
     * 홈 목록 우선순위:
     * 1) 두고 온 기기  2) 함께 있는 기기  3) 함께 이동 중  4) 그 외(최근 저장 시각 순)
     */
    private fun sortHomeItems(items: List<TrackedItem>): List<TrackedItem> =
        items.sortedWith(
            compareBy<TrackedItem> { homeSortPriority(it) }
                .thenByDescending { it.lastSpot?.savedAt ?: 0L }
                .thenBy { it.displayName.lowercase() }
        )

    private fun homeSortPriority(item: TrackedItem): Int = when {
        !item.enabled -> 4
        item.status == ItemStatus.LEFT_BEHIND -> 0
        item.status == ItemStatus.PRESENT -> 1
        item.carryingWhileDisconnected -> 2
        else -> 3
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
        // Play 정책: 시스템 위치 권한보다 백그라운드 위치 안내/동의가 먼저 나와야 함
        if (needsBackgroundDisclosure()) {
            if (force || !wasAsked()) {
                showBackgroundDisclosure()
            }
            return
        }
        requestCorePermissionsThenBackground(force)
    }

    private fun needsBackgroundDisclosure(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !PermissionHelper.hasBackgroundLocation(this) &&
            !backgroundDisclosureAccepted

    private fun showBackgroundDisclosure() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_background_location, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<View>(R.id.btnDialogAllow).setOnClickListener {
            backgroundDisclosureAccepted = true
            markAsked()
            dialog.dismiss()
            // 다이얼로그가 완전히 닫힌 뒤에 시스템 권한을 요청해야 창이 뜸
            binding.root.post { requestCorePermissionsThenBackground(force = true) }
        }
        dialogView.findViewById<View>(R.id.btnDialogLater).setOnClickListener {
            markAsked()
            dialog.dismiss()
            startServiceIfReady()
        }

        dialog.show()
    }

    private fun requestCorePermissionsThenBackground(force: Boolean) {
        val needed = mutableListOf<String>()
        // 대략 위치만 있어도 hasFineLocation=true가 될 수 있어, 정확한 위치를 따로 확인한다.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
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
            requestBackgroundLocationIfNeeded()
        }
    }

    private fun requestBackgroundLocationIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == android.content.pm.PackageManager.PERMISSION_GRANTED &&
            !PermissionHelper.hasBackgroundLocation(this)
        ) {
            if (!backgroundDisclosureAccepted) {
                showBackgroundDisclosure()
                return
            }
            binding.root.post {
                requestBackgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        } else {
            startServiceIfReady()
        }
    }

    private fun toggleMonitoring() {
        if (shouldRunMonitor()) {
            AppSettings.setMonitoringEnabled(this, false)
            MonitorService.stop(this)
            updateMonitoringStatus()
            return
        }
        AppSettings.setMonitoringEnabled(this, true)
        ensurePermissionsForMonitoring(force = true)
        startServiceIfReady()
    }

    private fun startServiceIfReady() {
        if (shouldRunMonitor()) {
            MonitorService.start(this)
        } else {
            MonitorService.stop(this)
        }
        updateMonitoringStatus()
    }

    private fun shouldRunMonitor(): Boolean =
        AppSettings.isMonitoringEnabled(this) &&
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
