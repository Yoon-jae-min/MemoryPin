package com.memorypin.ui

import android.Manifest
import android.content.Intent
import android.location.Geocoder
import android.net.Uri
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.memorypin.R
import com.memorypin.data.ItemIcon
import com.memorypin.data.MemoryPinRepository
import com.memorypin.data.Spot
import com.memorypin.data.TrackedItem
import com.memorypin.databinding.ActivitySpotDetailBinding
import com.memorypin.location.LocationProvider
import com.memorypin.util.AppSettings
import com.memorypin.util.PermissionHelper
import com.memorypin.util.TimeFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SpotDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySpotDetailBinding
    private lateinit var locationProvider: LocationProvider

    private var itemId: String? = null
    private var currentItem: TrackedItem? = null
    private var bindingSwitch = false

    private val requestLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) saveCurrentLocation() else toast(R.string.perm_location_rationale) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySpotDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        locationProvider = LocationProvider(applicationContext)
        itemId = intent.getStringExtra(EXTRA_ITEM_ID)
        if (itemId == null) {
            finish()
            return
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.nameRow.setOnClickListener { showRenameDialog() }
        binding.switchTracking.setOnCheckedChangeListener { _, checked ->
            if (bindingSwitch) return@setOnCheckedChangeListener
            itemId?.let { MemoryPinRepository.setEnabled(it, checked) }
        }
        binding.switchAlert.setOnCheckedChangeListener { _, checked ->
            if (bindingSwitch) return@setOnCheckedChangeListener
            itemId?.let { MemoryPinRepository.setAlertEnabled(it, checked) }
            updateSensitivityEnabled(checked)
        }
        binding.toggleSensitivity.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (bindingSwitch || !isChecked) return@addOnButtonCheckedListener
            val sensitivity = when (checkedId) {
                R.id.btnNear -> AppSettings.Sensitivity.NEAR
                R.id.btnFar -> AppSettings.Sensitivity.FAR
                else -> AppSettings.Sensitivity.NORMAL
            }
            itemId?.let { MemoryPinRepository.setSensitivity(it, sensitivity) }
            updateSensitivityHint(sensitivity)
        }
        binding.btnOpenMap.setOnClickListener { openMap() }
        binding.mapPreview.setOnClickListener { openMap() }
        binding.btnNavigate.setOnClickListener { navigate() }
        binding.btnSaveNow.setOnClickListener { onSaveNowClicked() }
        binding.btnSaveNowEmpty.setOnClickListener { onSaveNowClicked() }
        binding.btnRemove.setOnClickListener { confirmRemove() }

        observeItem()
    }

    override fun onPause() {
        super.onPause()
        saveMemoIfChanged()
    }

    private fun observeItem() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                MemoryPinRepository.items.collect { items ->
                    val item = items.firstOrNull { it.id == itemId }
                    if (item == null) {
                        finish()
                        return@collect
                    }
                    currentItem = item
                    bind(item)
                }
            }
        }
    }

    private fun bind(item: TrackedItem) {
        val icon = ItemIcon.fromKey(item.iconKey)
        binding.imgIcon.setImageResource(icon.drawableRes)
        binding.tvName.text = item.displayName
        binding.tvType.text = if (item.hasCustomName) {
            getString(R.string.edit_name_original, item.originalName)
        } else {
            getString(icon.labelRes)
        }

        bindingSwitch = true
        binding.switchTracking.isChecked = item.enabled
        binding.switchAlert.isChecked = item.alertEnabled
        val sensitivityButtonId = when (item.sensitivity) {
            AppSettings.Sensitivity.NEAR -> R.id.btnNear
            AppSettings.Sensitivity.NORMAL -> R.id.btnNormal
            AppSettings.Sensitivity.FAR -> R.id.btnFar
        }
        binding.toggleSensitivity.check(sensitivityButtonId)
        updateSensitivityHint(item.sensitivity)
        updateSensitivityEnabled(item.alertEnabled)
        bindingSwitch = false

        val spot = item.lastSpot
        if (spot == null) {
            binding.spotContent.visibility = View.GONE
            binding.noSpotState.visibility = View.VISIBLE
            return
        }

        binding.spotContent.visibility = View.VISIBLE
        binding.noSpotState.visibility = View.GONE

        binding.tvSavedAt.text = TimeFormat.full(spot.savedAt)
        binding.tvCoordinates.text = String.format(
            Locale.US, "%.6f, %.6f  ·  %s%dm",
            spot.latitude, spot.longitude,
            getString(R.string.detail_accuracy_prefix), spot.accuracy.toInt()
        )

        // 메모는 사용자가 입력 중일 수 있으니, 비어있을 때만 채운다.
        if (binding.editMemo.text.isNullOrEmpty()) {
            binding.editMemo.setText(spot.memo ?: "")
        }

        loadAddress(spot)
    }

    private fun showRenameDialog() {
        val item = currentItem ?: return
        val id = itemId ?: return
        val padding = (20 * resources.displayMetrics.density).toInt()
        val input = EditText(this).apply {
            setText(item.displayName)
            setSelection(text.length)
            hint = getString(R.string.edit_name_hint)
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
        val message = getString(R.string.edit_name_original, item.originalName)
        AlertDialog.Builder(this)
            .setTitle(R.string.edit_name_title)
            .setMessage(message)
            .setView(container)
            .setPositiveButton(R.string.edit_save) { _, _ ->
                val name = input.text?.toString().orEmpty()
                if (name.isBlank()) {
                    toast(R.string.edit_name_empty)
                    return@setPositiveButton
                }
                MemoryPinRepository.setDisplayName(id, name)
                toast(R.string.edit_name_saved)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun loadAddress(spot: Spot) {
        binding.tvAddress.text = getString(R.string.detail_address_loading)
        if (!Geocoder.isPresent()) {
            binding.tvAddress.text = getString(R.string.detail_address_unavailable)
            return
        }
        lifecycleScope.launch {
            val address = withContext(Dispatchers.IO) {
                runCatching {
                    val geocoder = Geocoder(this@SpotDetailActivity, Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val results = geocoder.getFromLocation(spot.latitude, spot.longitude, 1)
                    results?.firstOrNull()?.getAddressLine(0)
                }.getOrNull()
            }
            binding.tvAddress.text = address ?: getString(R.string.detail_address_unavailable)
        }
    }

    private fun onSaveNowClicked() {
        if (!PermissionHelper.hasFineLocation(this)) {
            requestLocationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        saveCurrentLocation()
    }

    private fun saveCurrentLocation() {
        val id = itemId ?: return
        lifecycleScope.launch {
            val location = locationProvider.getCurrent()
            if (location == null) {
                toast(R.string.detail_address_unavailable)
                return@launch
            }
            val spot = Spot(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = location.accuracy,
                savedAt = System.currentTimeMillis(),
                memo = binding.editMemo.text?.toString()?.ifBlank { null }
            )
            MemoryPinRepository.saveSpotForId(id, spot)
        }
    }

    private fun saveMemoIfChanged() {
        val id = itemId ?: return
        val item = currentItem ?: return
        if (item.lastSpot == null) return
        val memo = binding.editMemo.text?.toString()?.ifBlank { null }
        if (memo != item.lastSpot.memo) {
            MemoryPinRepository.updateMemo(id, memo)
        }
    }

    private fun updateSensitivityHint(sensitivity: AppSettings.Sensitivity) {
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

    private fun openMap() {
        val spot = currentItem?.lastSpot ?: return
        val label = Uri.encode(currentItem?.displayName ?: "")
        val geo = Uri.parse(
            "geo:${spot.latitude},${spot.longitude}?q=${spot.latitude},${spot.longitude}($label)"
        )
        val intent = Intent(Intent.ACTION_VIEW, geo)
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(Intent.createChooser(intent, getString(R.string.detail_map_chooser)))
        } else {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/maps/search/?api=1&query=${spot.latitude},${spot.longitude}")
                )
            )
        }
    }

    private fun navigate() {
        val spot = currentItem?.lastSpot ?: return
        val intents = buildNavigationIntents(spot.latitude, spot.longitude)
        if (intents.isEmpty()) {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${spot.latitude},${spot.longitude}")
                )
            )
            return
        }
        val chooser = Intent.createChooser(intents.first(), getString(R.string.detail_nav_chooser))
        if (intents.size > 1) {
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, intents.drop(1).toTypedArray())
        }
        startActivity(chooser)
    }

    private fun buildNavigationIntents(lat: Double, lng: Double): List<Intent> {
        val candidates = listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lng")),
            Intent(Intent.ACTION_VIEW, Uri.parse("kakaomap://route?ep=$lng,$lat&by=CAR")),
            Intent(Intent.ACTION_VIEW, Uri.parse("nmap://route/car?dlat=$lat&dlng=$lng&appname=$packageName")),
            Intent(Intent.ACTION_VIEW, Uri.parse("tmap://route?goalx=$lng&goaly=$lat")),
            Intent(Intent.ACTION_VIEW, Uri.parse("waze://?ll=$lat,$lng&navigate=yes")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng"))
        )
        val seen = mutableSetOf<String>()
        return candidates.mapNotNull { intent ->
            val resolved = intent.resolveActivity(packageManager) ?: return@mapNotNull null
            if (!seen.add(resolved.packageName)) null else intent
        }
    }

    private fun confirmRemove() {
        AlertDialog.Builder(this)
            .setTitle(R.string.edit_remove)
            .setMessage(currentItem?.displayName)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                val item = currentItem
                itemId?.let { MemoryPinRepository.remove(it) }
                item?.let { AppSettings.clearDeclinedForAutoRegister(this, it.sourceId) }
                finish()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun toast(resId: Int) {
        Toast.makeText(this, getString(resId), Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val EXTRA_ITEM_ID = "extra_item_id"
    }
}
