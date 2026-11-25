package com.memorypin.ui

import android.Manifest
import android.location.Geocoder
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.memorypin.R
import com.memorypin.connection.SourceType
import com.memorypin.data.DeclinedDevice
import com.memorypin.data.ItemIcon
import com.memorypin.data.MemoryPinRepository
import com.memorypin.data.QuietPlace
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

    private enum class PendingLocationAction {
        HOME, WORK, FAVORITE
    }

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var locationProvider: LocationProvider
    private var binding_ui = false
    private var pendingLocationAction = PendingLocationAction.HOME

    private val declinedAdapter = DeclinedDeviceAdapter(
        onTrackAgain = { trackAgain(it) },
        onRemove = { removeFromDeclinedList(it) }
    )

    private val favoriteAdapter = QuietPlaceAdapter(
        onRemove = { removeFavorite(it) }
    )

    private val requestLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            toast(R.string.perm_location_rationale)
            return@registerForActivityResult
        }
        when (pendingLocationAction) {
            PendingLocationAction.HOME -> registerHomeHere()
            PendingLocationAction.WORK -> registerWorkHere()
            PendingLocationAction.FAVORITE -> promptFavoriteNameThenRegister()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        locationProvider = LocationProvider(applicationContext)
        MemoryPinRepository.init(applicationContext)

        binding.recyclerDeclined.layoutManager = LinearLayoutManager(this)
        binding.recyclerDeclined.adapter = declinedAdapter
        binding.recyclerFavorites.layoutManager = LinearLayoutManager(this)
        binding.recyclerFavorites.adapter = favoriteAdapter
        binding.tvFavoriteDesc.text = getString(
            R.string.settings_favorite_desc,
            AppSettings.MAX_FAVORITE_PLACES
        )

        binding.btnBack.setOnClickListener { finish() }
        binding.btnSetHome.setOnClickListener { onSetHomeClicked() }
        binding.btnClearHome.setOnClickListener {
            AppSettings.clearHome(this)
            bindHomeSection()
            toast(R.string.settings_home_cleared)
        }
        binding.btnSetWork.setOnClickListener { onSetWorkClicked() }
        binding.btnClearWork.setOnClickListener {
            AppSettings.clearWork(this)
            bindWorkSection()
            toast(R.string.settings_work_cleared)
        }
        binding.btnAddFavorite.setOnClickListener { onAddFavoriteClicked() }

        binding.switchLeftBehind.setOnCheckedChangeListener { _, checked ->
            if (binding_ui) return@setOnCheckedChangeListener
            AppSettings.setLeftBehindEnabled(this, checked)
        }

        binding.switchAutoRegister.setOnCheckedChangeListener { _, checked ->
            if (binding_ui) return@setOnCheckedChangeListener
            AppSettings.setAutoRegisterOnConnectEnabled(this, checked)
        }

        bindCurrentValues()
    }

    override fun onResume() {
        super.onResume()
        bindHomeSection()
        bindWorkSection()
        bindFavoriteSection()
        bindDeclinedSection()
    }

    private fun bindCurrentValues() {
        binding_ui = true

        val enabled = AppSettings.isLeftBehindEnabled(this)
        binding.switchLeftBehind.isChecked = enabled
        binding.switchAutoRegister.isChecked = AppSettings.isAutoRegisterOnConnectEnabled(this)

        bindHomeSection()
        bindWorkSection()
        bindFavoriteSection()
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
        if (AppSettings.isMonitoringEnabled(this) && PermissionHelper.canMonitor(this)) {
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
            loadFixedPlaceAddress(
                AppSettings.getHome(this),
                binding.tvHomeAddress
            )
        } else {
            binding.tvHomeStatus.text = getString(R.string.settings_home_not_set)
            binding.tvHomeAddress.visibility = View.GONE
            binding.btnClearHome.visibility = View.GONE
        }
    }

    private fun bindWorkSection() {
        val work = AppSettings.getWork(this)
        if (work != null) {
            binding.tvWorkStatus.text = getString(R.string.settings_work_set_status)
            binding.btnClearWork.visibility = View.VISIBLE
            if (!work.address.isNullOrBlank()) {
                binding.tvWorkAddress.visibility = View.VISIBLE
                binding.tvWorkAddress.text = work.address
            } else {
                loadFixedPlaceAddress(
                    work.latitude to work.longitude,
                    binding.tvWorkAddress
                ) { address ->
                    AppSettings.updateQuietPlaceAddress(this, work.id, address)
                }
            }
        } else {
            binding.tvWorkStatus.text = getString(R.string.settings_work_not_set)
            binding.tvWorkAddress.visibility = View.GONE
            binding.btnClearWork.visibility = View.GONE
        }
    }

    private fun bindFavoriteSection() {
        val favorites = AppSettings.getFavoritePlaces(this)
        favoriteAdapter.submitList(favorites)
        val hasItems = favorites.isNotEmpty()
        binding.recyclerFavorites.visibility = if (hasItems) View.VISIBLE else View.GONE
        binding.tvFavoriteEmpty.visibility = if (hasItems) View.GONE else View.VISIBLE
        binding.btnAddFavorite.isEnabled = favorites.size < AppSettings.MAX_FAVORITE_PLACES
    }

    private fun removeFavorite(place: QuietPlace) {
        AppSettings.removeQuietPlace(this, place.id)
        bindFavoriteSection()
        toast(R.string.settings_favorite_removed)
    }

    private fun loadFixedPlaceAddress(
        coords: Pair<Double, Double>?,
        target: android.widget.TextView,
        onResolved: ((String?) -> Unit)? = null
    ) {
        if (coords == null) {
            target.visibility = View.GONE
            return
        }
        target.visibility = View.VISIBLE
        target.text = getString(R.string.settings_home_loading)
        resolveAddress(coords.first, coords.second) { address ->
            target.text = address ?: formatCoordinates(coords.first, coords.second)
            onResolved?.invoke(address)
        }
    }

    private fun resolveAddress(
        lat: Double,
        lng: Double,
        onResult: (String?) -> Unit
    ) {
        if (!Geocoder.isPresent()) {
            onResult(null)
            return
        }
        lifecycleScope.launch {
            val address = withContext(Dispatchers.IO) {
                runCatching {
                    val geocoder = Geocoder(this@SettingsActivity, Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val results = geocoder.getFromLocation(lat, lng, 1)
                    results?.firstOrNull()?.getAddressLine(0)
                }.getOrNull()
            }
            onResult(address)
        }
    }

    private fun formatCoordinates(lat: Double, lng: Double): String =
        String.format(Locale.US, "%.5f, %.5f", lat, lng)

    private fun onSetHomeClicked() {
        pendingLocationAction = PendingLocationAction.HOME
        if (!PermissionHelper.hasFineLocation(this)) {
            requestLocationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        registerHomeHere()
    }

    private fun onSetWorkClicked() {
        pendingLocationAction = PendingLocationAction.WORK
        if (!PermissionHelper.hasFineLocation(this)) {
            requestLocationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        registerWorkHere()
    }

    private fun onAddFavoriteClicked() {
        if (AppSettings.getFavoritePlaces(this).size >= AppSettings.MAX_FAVORITE_PLACES) {
            toast(getString(R.string.settings_favorite_full, AppSettings.MAX_FAVORITE_PLACES))
            return
        }
        pendingLocationAction = PendingLocationAction.FAVORITE
        if (!PermissionHelper.hasFineLocation(this)) {
            requestLocationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        promptFavoriteNameThenRegister()
    }

    private fun registerHomeHere() {
        lifecycleScope.launch {
            val location = locationProvider.getCurrent()
            if (location == null) {
                toast(R.string.detail_address_unavailable)
                return@launch
            }
            val address = withContext(Dispatchers.IO) {
                reverseGeocode(location.latitude, location.longitude)
            }
            AppSettings.setHome(
                this@SettingsActivity,
                location.latitude,
                location.longitude,
                address
            )
            bindHomeSection()
            toast(R.string.settings_home_saved)
        }
    }

    private fun registerWorkHere() {
        lifecycleScope.launch {
            val location = locationProvider.getCurrent()
            if (location == null) {
                toast(R.string.detail_address_unavailable)
                return@launch
            }
            val address = withContext(Dispatchers.IO) {
                reverseGeocode(location.latitude, location.longitude)
            }
            AppSettings.setWork(
                this@SettingsActivity,
                location.latitude,
                location.longitude,
                address
            )
            bindWorkSection()
            toast(R.string.settings_work_saved)
        }
    }

    private fun promptFavoriteNameThenRegister() {
        val padding = (20 * resources.displayMetrics.density).toInt()
        val input = EditText(this).apply {
            hint = getString(R.string.settings_favorite_name_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            maxLines = 1
            setPadding(padding)
        }
        val container = FrameLayout(this).apply {
            addView(
                input,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = padding
                    marginEnd = padding
                }
            )
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_favorite_name_title)
            .setView(container)
            .setPositiveButton(R.string.edit_save) { _, _ ->
                val name = input.text?.toString().orEmpty()
                if (name.isBlank()) {
                    toast(R.string.settings_favorite_name_empty)
                    return@setPositiveButton
                }
                registerFavoriteHere(name)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun registerFavoriteHere(label: String) {
        lifecycleScope.launch {
            val location = locationProvider.getCurrent()
            if (location == null) {
                toast(R.string.detail_address_unavailable)
                return@launch
            }
            val address = withContext(Dispatchers.IO) {
                reverseGeocode(location.latitude, location.longitude)
            }
            val place = AppSettings.addFavoritePlace(
                this@SettingsActivity,
                label,
                location.latitude,
                location.longitude,
                address
            )
            if (place == null) {
                toast(getString(R.string.settings_favorite_full, AppSettings.MAX_FAVORITE_PLACES))
                return@launch
            }
            bindFavoriteSection()
            toast(R.string.settings_favorite_saved)
        }
    }

    private fun reverseGeocode(lat: Double, lng: Double): String? {
        if (!Geocoder.isPresent()) return null
        return runCatching {
            val geocoder = Geocoder(this, Locale.getDefault())
            @Suppress("DEPRECATION")
            geocoder.getFromLocation(lat, lng, 1)?.firstOrNull()?.getAddressLine(0)
        }.getOrNull()
    }

    private fun toast(resId: Int) {
        Toast.makeText(this, getString(resId), Toast.LENGTH_SHORT).show()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
