package com.memorypin.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.memorypin.data.ItemIcon
import com.memorypin.databinding.ItemPairedDeviceBinding

/** 페어링된 블루투스 기기 한 건. */
data class PairedDevice(
    val name: String,
    val address: String,
    val added: Boolean
)

class PairedDeviceAdapter(
    private val onClick: (PairedDevice) -> Unit
) : ListAdapter<PairedDevice, PairedDeviceAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemPairedDeviceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(private val binding: ItemPairedDeviceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(device: PairedDevice) {
            val ctx = binding.root.context
            val icon = ItemIcon.guessFromName(device.name)

            binding.imgIcon.setImageResource(icon.drawableRes)
            binding.iconHolder.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(ctx, icon.colorRes))

            binding.tvDeviceName.text = device.name
            binding.tvAdded.visibility = if (device.added) android.view.View.VISIBLE else android.view.View.GONE
            binding.imgAdd.visibility = if (device.added) android.view.View.GONE else android.view.View.VISIBLE

            binding.root.isEnabled = !device.added
            binding.root.alpha = if (device.added) 0.6f else 1f
            binding.root.setOnClickListener { if (!device.added) onClick(device) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<PairedDevice>() {
            override fun areItemsTheSame(a: PairedDevice, b: PairedDevice) = a.address == b.address
            override fun areContentsTheSame(a: PairedDevice, b: PairedDevice) = a == b
        }
    }
}
