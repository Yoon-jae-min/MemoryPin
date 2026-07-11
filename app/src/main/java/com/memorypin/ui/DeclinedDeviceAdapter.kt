package com.memorypin.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.memorypin.data.DeclinedDevice
import com.memorypin.data.ItemIcon
import com.memorypin.databinding.ItemDeclinedDeviceBinding

class DeclinedDeviceAdapter(
    private val onTrackAgain: (DeclinedDevice) -> Unit,
    private val onRemove: (DeclinedDevice) -> Unit
) : ListAdapter<DeclinedDevice, DeclinedDeviceAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemDeclinedDeviceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(private val binding: ItemDeclinedDeviceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(device: DeclinedDevice) {
            val ctx = binding.root.context
            val icon = ItemIcon.guessFromName(device.displayName)

            binding.imgIcon.setImageResource(icon.drawableRes)
            binding.iconHolder.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(ctx, icon.colorRes))

            binding.tvDeviceName.text = device.displayName
            binding.tvDeviceAddress.text = device.sourceId
            binding.btnTrackAgain.setOnClickListener { onTrackAgain(device) }
            binding.btnRemoveFromList.setOnClickListener { onRemove(device) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<DeclinedDevice>() {
            override fun areItemsTheSame(a: DeclinedDevice, b: DeclinedDevice) =
                a.sourceId.equals(b.sourceId, ignoreCase = true)

            override fun areContentsTheSame(a: DeclinedDevice, b: DeclinedDevice) = a == b
        }
    }
}
