package com.memorypin.ui

import android.Manifest
import android.location.Geocoder
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.memorypin.R
import com.memorypin.connection.SourceType
import com.memorypin.data.DeclinedDevice
import com.memorypin.data.ItemIcon
import com.memorypin.data.MemoryPinRepository
import com.memorypin.databinding.ActivitySettingsBinding
import com.memorypin.location.LocationProvider
import com.memorypin.service.MonitorService
import com.memorypin.util.AppSettings
import com.memorypin.util.PermissionHelper
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var locationProvider: LocationProvider
    private var binding_ui = false

    private val declinedAdapter = DeclinedDeviceAdapter(
        onTrackAgain = { trackAgain(it) },
        onRemove = { removeFromDeclinedList(it) }
    )

    private val requestLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) registerHomeHere() else toast(R.string.perm_location_rationale)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        locationProvider = LocationProvider(applicationContext)
        MemoryPinRepository.init(applicationContext)

        binding.recyclerDeclined.layoutManager = LinearLayoutManager(this)
        binding.recyclerDeclined.adapter = declinedAdapter

        binding.btnBack.setOnClickListener { finish() }
        binding.btnSetHome.setOnClickListener { onSetHomeClicked() }
        binding.btnClearHome.setOnClickListener {
            AppSettings.clearHome(this)
            bindHomeSection()
            toast(R.string.settings_home_cleared)
        }

        binding.switchLeftBehind.setOnCheckedChangeListener { _, checked ->
            if (binding_ui) return@setOnCheckedChangeListener
            AppSettings.setLeftBehindEnabled(this, checked)
            updateSensitivityEnabled(checked)
        }

        binding.switchAutoRegister.setOnCheckedChangeListener { _, checked ->
            if (binding_ui) return@setOnCheckedChangeListener
            AppSettings.setAutoRegisterOnConnectEnabled(this, checked)
        }

        binding.toggleSensitivity.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (binding_ui || !isChecked) return@addOnButtonCheckedListener
            val sensitivity = when (checkedId) {
                R.id.btnNear -> AppSettings.Sensitivity.NEAR
                R.id.btnFar -> AppSettings.Sensitivity.FAR
                else -> AppSettings.Sensitivity.NORMAL
            }
            AppSettings.setSensitivity(this, sensitivity)
            updateHint(sensitivity)
        }

        bindCurrentValues()
    }

    override fun onResume() {
        super.onResume()
        bindHomeSection()
        bindDeclinedSection()
    }

    private fun bindCurrentValues() {
        binding_ui = true

        val enabled = AppSettings.isLeftBehindEnabled(this)
        binding.switchLeftBehind.isChecked = enabled
        binding.switchAutoRegister.isChecked = AppSettings.isAutoRegisterOnConnectEnabled(this)

        val sensitivity = AppSettings.getSensitivity(this)
        val buttonId = when (sensitivity) {
            AppSettings.Sensitivity.NEAR -> R.id.btnNear
            AppSettings.Sensitivity.NORMAL -> R.id.btnNormal
            AppSettings.Sensitivity.FAR -> R.id.btnFar
        }
        binding.toggleSensitivity.check(buttonId)
        updateHint(sensitivity)
        updateSensitivityEnabled(enabled)
        bindHomeSection()
        bindDeclinedSection()

        binding_ui = false
    }

    private fun bindDeclinedSection() {
        val devices = AppSettings.getDeclinedDevices(this)
        declinedAdapter.submitList(devices)
        val hasItems = devices.isNotEmpty()
        binding.recyclerDeclined.visibility = if (hasItems) View.VISIBLE else View.GONE
        binding.tvDeclinedEmpty.visibility = if (hasItems) View.GONE else View.VISIBLE
    }

    private fun trackAgain(device: DeclinedDevice) {
        AppSettings.clearDeclinedForAutoRegister(this, device.sourceId)
        val icon = ItemIcon.guessFromName(device.displayName)
        MemoryPinRepository.addItem(
            sourceType = SourceType.BLUETOOTH,
            sourceId = device.sourceId,
            displayName = device.displayName,
            iconKey = icon.key
        )
        if (PermissionHelper.canMonitor(this)) {
            MonitorService.start(this)
        }
        bindDeclinedSection()
        toast(getString(R.string.settings_declined_tracked_again, device.displayName))
    }

    private fun removeFromDeclinedList(device: DeclinedDevice) {
        AppSettings.clearDeclinedForAutoRegister(this, device.sourceId)
        bindDeclinedSection()
        toast(R.string.settings_declined_removed)
    }

    private fun bindHomeSection() {
        if (AppSettings.hasHome(this)) {
            binding.tvHomeStatus.text = getString(R.string.settings_home_set_status)
            binding.btnClearHome.visibility = View.VISIBLE
            loadHomeAddress()
        } else {
            binding.tvHomeStatus.text = getString(R.string.settings_home_not_set)
            binding.tvHomeAddress.visibility = View.GONE
            binding.btnClearHome.visibility = View.GONE
        }
    }

    private fun loadHomeAddress() {
        val home = AppSettings.getHome(this) ?: return
        binding.tvHomeAddress.visibility = View.VISIBLE
        binding.tvHomeAddress.text = getString(R.string.settings_home_loading)

        if (!Geocoder.isPresent()) {
            binding.tvHomeAddress.text = formatCoordinates(home.first, home.second)
            return
        }

        lifecycleScope.launch {
            val address = withContext(Dispatchers.IO) {
                runCatching {
                    val geocoder = Geocoder(this@SettingsActivity, Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val results = geocoder.getFromLocation(home.first, home.second, 1)
                    results?.firstOrNull()?.getAddressLine(0)
                }.getOrNull()
            }
            binding.tvHomeAddress.text =
                address ?: formatCoordinates(home.first, home.second)
        }
    }

    private fun formatCoordinates(lat: Double, lng: Double): String =
        String.format(Locale.US, "%.5f, %.5f", lat, lng)

    private fun onSetHomeClicked() {
        if (!PermissionHelper.hasFineLocation(this)) {
            requestLocationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        registerHomeHere()
    }

    private fun registerHomeHere() {
        lifecycleScope.launch {
            val location = locationProvider.getCurrent()
            if (location == null) {
                toast(R.string.detail_address_unavailable)
                return@launch
            }
            AppSettings.setHome(this@SettingsActivity, location.latitude, location.longitude)
            bindHomeSection()
            toast(R.string.settings_home_saved)
        }
    }

    private fun updateHint(sensitivity: AppSettings.Sensitivity) {
        binding.tvSensitivityHint.text = getString(
            when (sensitivity) {
                AppSettings.Sensitivity.NEAR -> R.string.sensitivity_near_hint
                AppSettings.Sensitivity.NORMAL -> R.string.sensitivity_normal_hint
                AppSettings.Sensitivity.FAR -> R.string.sensitivity_far_hint
            }
        )
    }

    private fun updateSensitivityEnabled(enabled: Boolean) {
        binding.sensitivityCard.alpha = if (enabled) 1f else 0.5f
        binding.toggleSensitivity.isEnabled = enabled
        for (i in 0 until binding.toggleSensitivity.childCount) {
            binding.toggleSensitivity.getChildAt(i).isEnabled = enabled
        }
    }

    private fun toast(resId: Int) {
        Toast.makeText(this, getString(resId), Toast.LENGTH_SHORT).show()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
