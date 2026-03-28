package com.example.helmetcompanion

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.helmetcompanion.databinding.ItemContactBinding

class EmergencyContactAdapter(
    private val onRemove: (EmergencyContact) -> Unit
) : RecyclerView.Adapter<EmergencyContactAdapter.ContactViewHolder>() {

    private val items = mutableListOf<EmergencyContact>()

    fun submitList(newItems: List<EmergencyContact>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ContactViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ContactViewHolder(
        private val binding: ItemContactBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: EmergencyContact) {
            binding.textContactName.text = contact.displayName
            binding.textContactPhone.text = contact.phoneNumber
            contact.photoUri?.let { binding.imageContact.setImageURI(Uri.parse(it)) }
            binding.btnRemoveContact.setOnClickListener { onRemove(contact) }
        }
    }
}
