package com.example.helmetcompanion

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.helmetcompanion.databinding.ItemManeuverBinding

class ManeuverAdapter : RecyclerView.Adapter<ManeuverAdapter.ManeuverViewHolder>() {

    private val items = mutableListOf<RouteManeuver>()

    fun submitList(newItems: List<RouteManeuver>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ManeuverViewHolder {
        val binding = ItemManeuverBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ManeuverViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ManeuverViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ManeuverViewHolder(
        private val binding: ItemManeuverBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(maneuver: RouteManeuver) {
            binding.textManeuverInstruction.text = maneuver.instruction
            binding.textManeuverMeta.text =
                "${maneuver.distanceMeters} m • ${maneuver.helmetCommand}"
            binding.textManeuverArrow.text = maneuver.helmetCommand
        }
    }
}
