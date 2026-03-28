package com.example.helmetcompanion

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.helmetcompanion.databinding.FragmentCrashAlertBinding

class CrashAlertFragment : Fragment() {

    private var _binding: FragmentCrashAlertBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels {
        MainViewModelFactory(requireActivity().application)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCrashAlertBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSimulateCrash.setOnClickListener {
            viewModel.handleHelmetEvent(HelmetEvent(HelmetEventType.CRASH, "CRASH"))
        }
        binding.btnReconnectHelmet.setOnClickListener { viewModel.connectHelmet() }
        binding.btnCancel.setOnClickListener { viewModel.cancelCrashAlert() }
        binding.btnSendSOS.setOnClickListener {
            viewModel.sendSos { _, message ->
                if (isAdded) {
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        viewModel.crashAlertState.observe(viewLifecycleOwner) { state ->
            binding.statusText.text = state.message
            binding.countdownText.text = when (state.phase) {
                CrashAlertPhase.COUNTDOWN -> "Automatic SOS in ${state.secondsRemaining}s"
                else -> state.details
            }
            binding.alertMessageText.text = state.details
            binding.btnCancel.isEnabled = state.phase == CrashAlertPhase.COUNTDOWN ||
                state.phase == CrashAlertPhase.CRASH_DETECTED
            binding.btnSendSOS.isEnabled = state.phase != CrashAlertPhase.SOS_SENDING
        }

        viewModel.bluetoothState.observe(viewLifecycleOwner) { state ->
            binding.chipHelmetStatus.text = when (state.status) {
                BluetoothConnectionStatus.CONNECTED -> "${state.deviceName} connected"
                BluetoothConnectionStatus.CONNECTING -> "Connecting to ${state.deviceName}..."
                BluetoothConnectionStatus.PERMISSION_REQUIRED -> "Bluetooth permission"
                BluetoothConnectionStatus.DEVICE_NOT_FOUND -> "${state.deviceName} not paired"
                BluetoothConnectionStatus.ERROR -> "Connection issue"
                BluetoothConnectionStatus.DISCONNECTED -> "Helmet offline"
            }
            binding.btnReconnectHelmet.text = when (state.status) {
                BluetoothConnectionStatus.CONNECTED -> "Helmet Connected"
                BluetoothConnectionStatus.CONNECTING -> "Connecting..."
                else -> getString(R.string.crash_action_reconnect)
            }
            binding.btnReconnectHelmet.isEnabled = state.status != BluetoothConnectionStatus.CONNECTING
            binding.alertMessageText.text = when (state.status) {
                BluetoothConnectionStatus.DEVICE_NOT_FOUND ->
                    "Phone cannot find ${state.deviceName}. Pair it in Bluetooth settings first, then tap Connect to Helmet."
                BluetoothConnectionStatus.PERMISSION_REQUIRED ->
                    "Bluetooth permission is required before the app can connect to the helmet."
                else -> binding.alertMessageText.text
            }
        }

        viewModel.contacts.observe(viewLifecycleOwner) { contacts ->
            binding.chipContactsStatus.text = "${contacts.size} emergency contacts"
        }

        AppContainer.from(requireContext()).locationRepository.getLastKnownLocation { location ->
            if (isAdded) {
                binding.locationStatusText.text = if (location != null) {
                    "Last known location ready for SOS: ${location.latitude}, ${location.longitude}"
                } else {
                    "Location is not ready yet. Enable GPS for the best SOS message."
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
