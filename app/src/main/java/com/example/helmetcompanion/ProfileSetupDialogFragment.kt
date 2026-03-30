package com.example.helmetcompanion

import android.app.Dialog
import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.widget.Toast
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
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            selectedPhotoUri = uri
            if (uri != null) {
                // Tell Android to keep this permission permanently
                requireContext().contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                binding.imageRiderPhoto.setImageURI(uri)
            }
        }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogProfileSetupBinding.inflate(LayoutInflater.from(requireContext()))
        isCancelable = false

        val existing = viewModel.profile.value ?: RiderProfile()
        selectedPhotoUri = existing.riderPhotoUri?.let(Uri::parse)
        if (selectedPhotoUri != null) {
            try {
                // FIX: Actually try to open the file to test the permission first
                val testStream = requireContext().contentResolver.openInputStream(selectedPhotoUri!!)
                testStream?.close()

                // If it didn't crash above, it's safe to give to the ImageView
                binding.imageRiderPhoto.setImageURI(selectedPhotoUri)
            } catch (e: SecurityException) {
                // Permission denied! It's an old URI.
                e.printStackTrace()
                selectedPhotoUri = null
            } catch (e: Exception) {
                // File was deleted or moved
                e.printStackTrace()
                selectedPhotoUri = null
            }
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

        // 1. Prevent the user from typing freely into the field
        binding.editDob.isFocusable = false
        binding.editDob.isClickable = true

        // 2. Open the Calendar when clicked
        binding.editDob.setOnClickListener {
            val datePicker = com.google.android.material.datepicker.MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select Date of Birth")
                .setSelection(com.google.android.material.datepicker.MaterialDatePicker.todayInUtcMilliseconds())
                .build()

            datePicker.addOnPositiveButtonClickListener { selection ->
                // Convert the selected timestamp into a readable date string
                val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                val formattedDate = sdf.format(java.util.Date(selection))
                binding.editDob.setText(formattedDate)
            }

            // childFragmentManager ensures the picker survives dialog lifecycle changes
            datePicker.show(childFragmentManager, "DOB_PICKER")
        }

        // FIX 3: Pass the MIME type as an array
        binding.btnPickPhoto.setOnClickListener { photoPicker.launch(arrayOf("image/*")) }
        binding.btnSaveProfile.setOnClickListener { saveProfile() }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.settings_complete_profile))
            .setMessage(getString(R.string.settings_complete_profile_message))
            .setView(binding.root)
            .create().apply {
                setCanceledOnTouchOutside(false)
                setOnKeyListener { _: DialogInterface, keyCode: Int, event: KeyEvent ->
                    if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                        if (isCurrentFormComplete() || (viewModel.profile.value?.isComplete() == true)) {
                            dismiss()
                        }
                        true
                    } else {
                        false
                    }
                }
            }
    }

    private fun saveProfile() {
        val fullName = binding.editFullName.text?.toString().orEmpty().trim()
        val phone = binding.editPhone.text?.toString().orEmpty().trim()
        val city = binding.editCity.text?.toString().orEmpty().trim()
        val dob = binding.editDob.text?.toString().orEmpty().trim()
        val address = binding.editAddress.text?.toString().orEmpty().trim()
        val bloodGroup = binding.editBloodGroup.text?.toString().orEmpty().trim().uppercase()
        val vehicleType = binding.editVehicleType.text?.toString().orEmpty().trim()
        val vehicleModel = binding.editVehicleModel.text?.toString().orEmpty().trim()
        val vehicleReg = binding.editVehicleRegistration.text?.toString().orEmpty().trim().uppercase()

        var isValid = true

        // 1. Regex Validations
        if (!fullName.matches("^[a-zA-Z\\s]+$".toRegex())) {
            binding.editFullName.error = "Name must contain only alphabets"
            isValid = false
        } else binding.editFullName.error = null

        if (!phone.matches("^[0-9]{10}$".toRegex())) {
            binding.editPhone.error = "Enter a valid 10-digit phone number"
            isValid = false
        } else binding.editPhone.error = null

        if (!bloodGroup.matches("^(A|B|AB|O)[+-]$".toRegex())) {
            binding.editBloodGroup.error = "Invalid format (e.g., O+, AB-)"
            isValid = false
        } else binding.editBloodGroup.error = null

        if (!vehicleReg.matches("^[A-Z0-9]{6,11}$".toRegex())) {
            binding.editVehicleRegistration.error = "Enter a valid alphanumeric registration"
            isValid = false
        } else binding.editVehicleRegistration.error = null

        // 2. Empty Field Validations
        val requiredFields = listOf(
            city to binding.editCity,
            dob to binding.editDob,
            address to binding.editAddress,
            vehicleType to binding.editVehicleType,
            vehicleModel to binding.editVehicleModel
        )

        for ((text, view) in requiredFields) {
            if (text.isBlank()) {
                view.error = "This field cannot be empty"
                isValid = false
            } else view.error = null
        }

        // 3. Stop if anything failed
        if (!isValid) return

        binding.btnSaveProfile.isEnabled = false

        // 4. Proceed to save
        val current = FirebaseAuth.getInstance().currentUser
        val profile = RiderProfile(
            fullName = fullName,
            email = current?.email.orEmpty(),
            phoneNumber = phone,
            city = city,
            dateOfBirth = dob,
            address = address,
            bloodGroup = bloodGroup,
            riderPhotoUri = selectedPhotoUri?.toString(),
            vehicleType = vehicleType,
            vehicleModel = vehicleModel,
            vehicleRegistration = vehicleReg
        )

        viewModel.saveProfile(profile) { success ->
            if (isAdded && !success) {
                Toast.makeText(
                    requireContext(),
                    "Profile saved locally. Cloud sync can retry later.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            if (isAdded) {
                dismissAllowingStateLoss()
            }
        }
    }

    private fun isCurrentFormComplete(): Boolean {
        return RiderProfile(
            fullName = binding.editFullName.text?.toString().orEmpty().trim(),
            phoneNumber = binding.editPhone.text?.toString().orEmpty().trim(),
            city = binding.editCity.text?.toString().orEmpty().trim(),
            dateOfBirth = binding.editDob.text?.toString().orEmpty().trim(),
            address = binding.editAddress.text?.toString().orEmpty().trim(),
            bloodGroup = binding.editBloodGroup.text?.toString().orEmpty().trim(),
            vehicleType = binding.editVehicleType.text?.toString().orEmpty().trim(),
            vehicleModel = binding.editVehicleModel.text?.toString().orEmpty().trim(),
            vehicleRegistration = binding.editVehicleRegistration.text?.toString().orEmpty().trim()
        ).isComplete()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ProfileSetupDialog"
    }
}
