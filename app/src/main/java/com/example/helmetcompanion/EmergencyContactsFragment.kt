package com.example.helmetcompanion

import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.helmetcompanion.databinding.FragmentEmergencyContactsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText

class EmergencyContactsFragment : Fragment() {

    private var _binding: FragmentEmergencyContactsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels {
        MainViewModelFactory(requireActivity().application)
    }

    private lateinit var adapter: EmergencyContactAdapter

    private val contactPicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.data ?: return@registerForActivityResult
            importContactFromUri(uri)
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEmergencyContactsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = EmergencyContactAdapter { contact ->
            viewModel.removeEmergencyContact(contact.id)
        }
        binding.recyclerContacts.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerContacts.adapter = adapter

        binding.btnAddManual.setOnClickListener { showManualAddDialog() }
        binding.btnImportContact.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
            contactPicker.launch(intent)
        }

        viewModel.contacts.observe(viewLifecycleOwner) { contacts ->
            adapter.submitList(contacts)
            binding.textEmptyContacts.visibility = if (contacts.isEmpty()) View.VISIBLE else View.GONE
            binding.textContactsHint.text = "${contacts.size} trusted contact(s) will receive your SOS alerts."
        }
    }

    private fun importContactFromUri(uri: Uri) {
        queryContact(uri)?.let { draft ->
            viewModel.addEmergencyContact(draft)
            Toast.makeText(requireContext(), "Emergency contact added", Toast.LENGTH_SHORT).show()
        } ?: run {
            Toast.makeText(requireContext(), "Could not import contact", Toast.LENGTH_SHORT).show()
        }
    }

    private fun queryContact(uri: Uri): ContactDraft? {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI
        )
        val cursor: Cursor? = requireContext().contentResolver.query(uri, projection, null, null, null)
        cursor?.use {
            if (it != null && it.moveToFirst()) {
                val name = it.getString(0).orEmpty()
                val phone = it.getString(1).orEmpty()
                val photo = it.getString(2)?.let(Uri::parse)
                if (phone.isBlank()) return null
                return ContactDraft(
                    displayName = name,
                    phoneNumber = phone,
                    photoUri = photo,
                    fromDeviceContacts = true
                )
            }
        }
        return null
    }

    private fun showManualAddDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_manual_contact, null)
        val nameField = view.findViewById<TextInputEditText>(R.id.editManualContactName)
        val phoneField = view.findViewById<TextInputEditText>(R.id.editManualContactPhone)

        val dialog: AlertDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add emergency contact")
            .setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Add", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val phone = phoneField.text?.toString().orEmpty().trim()
                if (phone.length < 10) {
                    phoneField.error = "Enter a valid phone number"
                    return@setOnClickListener
                }
                viewModel.addEmergencyContact(
                    ContactDraft(
                        displayName = nameField.text?.toString().orEmpty().trim(),
                        phoneNumber = phone
                    )
                )
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
