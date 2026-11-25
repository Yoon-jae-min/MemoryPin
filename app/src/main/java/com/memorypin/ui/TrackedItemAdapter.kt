package com.memorypin.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.memorypin.R
import com.memorypin.data.ItemIcon
import com.memorypin.data.ItemStatus
import com.memorypin.data.TrackedItem
import com.memorypin.databinding.ItemTrackedBinding
import com.memorypin.util.TimeFormat

class TrackedItemAdapter(
    private val onClick: (TrackedItem) -> Unit
) : ListAdapter<TrackedItem, TrackedItemAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemTrackedBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(private val binding: ItemTrackedBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TrackedItem) {
            val ctx = binding.root.context
            val icon = ItemIcon.fromKey(item.iconKey)

            binding.imgIcon.setImageResource(icon.drawableRes)
            binding.iconHolder.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(ctx, icon.colorRes))

            binding.tvName.text = item.displayName

            binding.tvSubtitle.text = when {
                !item.enabled -> ctx.getString(R.string.item_disabled)
                item.carryingWhileDisconnected -> ctx.getString(R.string.item_carrying)
                item.status == ItemStatus.LEFT_BEHIND -> ctx.getString(R.string.item_left_behind)
                item.lastSpot == null -> ctx.getString(R.string.item_no_spot)
                item.status == ItemStatus.PRESENT -> ctx.getString(R.string.item_present)
                else -> ctx.getString(R.string.item_saved_prefix) +
                    TimeFormat.relative(item.lastSpot.savedAt)
            }

            val dotColor = when {
                !item.enabled -> R.color.on_surface_variant
                item.carryingWhileDisconnected -> R.color.type_watch
                item.status == ItemStatus.LEFT_BEHIND -> R.color.error
                item.lastSpot == null -> R.color.on_surface_variant
                item.status == ItemStatus.PRESENT -> R.color.success
                else -> R.color.primary
            }
            binding.statusDot.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(ctx, dotColor))

            binding.root.alpha = if (item.enabled) 1f else 0.6f
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<TrackedItem>() {
            override fun areItemsTheSame(a: TrackedItem, b: TrackedItem) = a.id == b.id
            override fun areContentsTheSame(a: TrackedItem, b: TrackedItem) = a == b
        }
    }
}
