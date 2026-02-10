package com.example.helmetcompanion

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment

class EmergencyContactsFragment : Fragment() {

    private lateinit var editNumber: EditText
    private lateinit var btnAdd: Button
    private lateinit var listView: ListView

    private val contacts = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(R.layout.fragment_emergency_contacts, container, false)

        editNumber = view.findViewById(R.id.editNumber)
        btnAdd = view.findViewById(R.id.btnAdd)
        listView = view.findViewById(R.id.listContacts)

        loadContacts()

        adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            contacts
        )
        listView.adapter = adapter

        btnAdd.setOnClickListener {
            val number = editNumber.text.toString().trim()
            if (number.length >= 10) {
                contacts.add(number)
                saveContacts()
                adapter.notifyDataSetChanged()
                editNumber.text.clear()
            } else {
                Toast.makeText(context, "Invalid number", Toast.LENGTH_SHORT).show()
            }
        }

        listView.setOnItemLongClickListener { _, _, position, _ ->
            contacts.removeAt(position)
            saveContacts()
            adapter.notifyDataSetChanged()
            true
        }

        return view
    }

    private fun loadContacts() {
        val prefs = requireContext()
            .getSharedPreferences("sos_prefs", Context.MODE_PRIVATE)

        val saved = prefs.getString("contacts", "")
        if (!saved.isNullOrEmpty()) {
            contacts.addAll(saved.split(","))
        }
    }

    private fun saveContacts() {
        val prefs = requireContext()
            .getSharedPreferences("sos_prefs", Context.MODE_PRIVATE)

        prefs.edit()
            .putString("contacts", contacts.joinToString(","))
            .apply()
    }
}
