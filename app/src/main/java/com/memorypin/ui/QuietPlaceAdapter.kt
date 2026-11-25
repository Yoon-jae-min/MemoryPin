package com.memorypin.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.memorypin.data.QuietPlace
import com.memorypin.databinding.ItemQuietPlaceBinding

class QuietPlaceAdapter(
    private val onRemove: (QuietPlace) -> Unit
) : ListAdapter<QuietPlace, QuietPlaceAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemQuietPlaceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(private val binding: ItemQuietPlaceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(place: QuietPlace) {
            binding.tvPlaceName.text = place.label
            binding.tvPlaceAddress.text = place.address
                ?: String.format("%.5f, %.5f", place.latitude, place.longitude)
            binding.btnRemovePlace.setOnClickListener { onRemove(place) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<QuietPlace>() {
            override fun areItemsTheSame(a: QuietPlace, b: QuietPlace) = a.id == b.id
            override fun areContentsTheSame(a: QuietPlace, b: QuietPlace) = a == b
        }
    }
}
