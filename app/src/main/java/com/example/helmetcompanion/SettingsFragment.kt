package com.example.helmetcompanion

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.helmetcompanion.databinding.FragmentSettingsBinding
import com.google.firebase.auth.FirebaseAuth

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels {
        MainViewModelFactory(requireActivity().application)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnEditProfile.setOnClickListener {
            ProfileSetupDialogFragment().show(parentFragmentManager, ProfileSetupDialogFragment.TAG)
        }
        binding.btnSaveSettings.setOnClickListener { saveSettings() }
        binding.btnSignOut.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            (activity as? MainActivity)?.startGoogleSignIn()
        }

        viewModel.profile.observe(viewLifecycleOwner) { profile ->
            binding.textProfileName.text =
                if (profile.fullName.isBlank()) "Complete your rider profile" else profile.fullName
            binding.textProfileMeta.text =
                listOf(profile.city, profile.phoneNumber, profile.vehicleRegistration)
                    .filter { it.isNotBlank() }
                    .joinToString(" • ")
                    .ifBlank { "Add rider, medical, and vehicle details for safer SOS alerts." }
            profile.riderPhotoUri?.let {
                binding.imageProfile.setImageURI(Uri.parse(it))
            }
        }

        viewModel.settings.observe(viewLifecycleOwner) { settings ->
            binding.editDeviceName.setText(settings.pairedDeviceName)
            binding.editCountdown.setText(settings.sosCountdownSeconds.toString())
            binding.editSosMessage.setText(settings.sosMessageTemplate)
            binding.switchShareLocation.isChecked = settings.includeLocationInSos
        }

        viewModel.bluetoothState.observe(viewLifecycleOwner) { state ->
            binding.textBluetoothState.text = "Helmet link: ${state.status.name.lowercase().replace("_", " ")}"
        }
    }

    private fun saveSettings() {
        val countdown = binding.editCountdown.text?.toString()?.toIntOrNull()?.coerceIn(5, 30) ?: 10
        viewModel.updateSettings(
            AppSettings(
                pairedDeviceName = binding.editDeviceName.text?.toString().orEmpty().ifBlank {
                    BuildConfig.HELMET_DEVICE_NAME
                },
                sosCountdownSeconds = countdown,
                includeLocationInSos = binding.switchShareLocation.isChecked,
                sosMessageTemplate = binding.editSosMessage.text?.toString().orEmpty().ifBlank {
                    "Emergency alert from Helmet Companion. Crash detected and help is needed immediately."
                }
            )
        )
        Toast.makeText(requireContext(), "Settings saved", Toast.LENGTH_SHORT).show()
        viewModel.connectHelmet()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
