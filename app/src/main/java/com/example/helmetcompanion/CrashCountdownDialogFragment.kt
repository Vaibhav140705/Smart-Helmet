package com.example.helmetcompanion

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.example.helmetcompanion.databinding.DialogCrashAlertBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class CrashCountdownDialogFragment : DialogFragment() {

    private lateinit var binding: DialogCrashAlertBinding
    private val viewModel: MainViewModel by activityViewModels {
        MainViewModelFactory(requireActivity().application)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogCrashAlertBinding.inflate(layoutInflater)
        isCancelable = false

        viewModel.crashAlertState.observe(this) { state ->
            binding.textCrashTitle.text = state.message
            binding.textCrashMessage.text = state.details
            binding.textCrashMeta.text = "Emergency contacts: ${viewModel.contacts.value?.size ?: 0}"
            binding.textCrashCountdown.text = when (state.phase) {
                CrashAlertPhase.COUNTDOWN -> "Automatic SOS in ${state.secondsRemaining}s"
                CrashAlertPhase.SOS_SENDING -> "Sending SOS..."
                else -> ""
            }
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .setNegativeButton(R.string.crash_action_ok) { _, _ ->
                viewModel.cancelCrashAlert()
            }
            .setPositiveButton(R.string.crash_action_sos) { _, _ ->
                viewModel.sendSos { _, _ -> }
            }
            .create()
    }

    companion object {
        const val TAG = "CrashCountdownDialog"
    }
}
