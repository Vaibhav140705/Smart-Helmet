package com.example.helmetcompanion

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.example.helmetcompanion.databinding.DialogProfileSetupBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth

class ProfileSetupDialogFragment : DialogFragment() {

    private var _binding: DialogProfileSetupBinding? = null
    private val binding get() = _binding!!
    private var selectedPhotoUri: Uri? = null

    private val viewModel: MainViewModel by activityViewModels {
        MainViewModelFactory(requireActivity().application)
    }

    private val photoPicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            selectedPhotoUri = uri
            if (uri != null) {
                binding.imageRiderPhoto.setImageURI(uri)
            }
        }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogProfileSetupBinding.inflate(LayoutInflater.from(requireContext()))
        isCancelable = false

        val existing = viewModel.profile.value ?: RiderProfile()
        selectedPhotoUri = existing.riderPhotoUri?.let(Uri::parse)
        if (selectedPhotoUri != null) {
            binding.imageRiderPhoto.setImageURI(selectedPhotoUri)
        }
        binding.editFullName.setText(existing.fullName)
        binding.editPhone.setText(existing.phoneNumber)
        binding.editCity.setText(existing.city)
        binding.editDob.setText(existing.dateOfBirth)
        binding.editAddress.setText(existing.address)
        binding.editBloodGroup.setText(existing.bloodGroup)
        binding.editVehicleType.setText(existing.vehicleType)
        binding.editVehicleModel.setText(existing.vehicleModel)
        binding.editVehicleRegistration.setText(existing.vehicleRegistration)

        binding.btnPickPhoto.setOnClickListener { photoPicker.launch("image/*") }
        binding.btnSaveProfile.setOnClickListener { saveProfile() }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.settings_complete_profile))
            .setMessage(getString(R.string.settings_complete_profile_message))
            .setView(binding.root)
            .create()
    }

    private fun saveProfile() {
        val current = FirebaseAuth.getInstance().currentUser
        val profile = RiderProfile(
            fullName = binding.editFullName.text?.toString().orEmpty().trim(),
            email = current?.email.orEmpty(),
            phoneNumber = binding.editPhone.text?.toString().orEmpty().trim(),
            city = binding.editCity.text?.toString().orEmpty().trim(),
            dateOfBirth = binding.editDob.text?.toString().orEmpty().trim(),
            address = binding.editAddress.text?.toString().orEmpty().trim(),
            bloodGroup = binding.editBloodGroup.text?.toString().orEmpty().trim(),
            riderPhotoUri = selectedPhotoUri?.toString(),
            vehicleType = binding.editVehicleType.text?.toString().orEmpty().trim(),
            vehicleModel = binding.editVehicleModel.text?.toString().orEmpty().trim(),
            vehicleRegistration = binding.editVehicleRegistration.text?.toString().orEmpty().trim()
        )
        if (!profile.isComplete()) {
            binding.editFullName.error = "Fill all rider and vehicle fields"
            return
        }
        viewModel.saveProfile(profile) { success ->
            if (success) dismissAllowingStateLoss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ProfileSetupDialog"
    }
}
