package com.example.helmetcompanion

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.telephony.SmsManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

class CrashAlertFragment : Fragment() {

    private lateinit var statusText: TextView
    private lateinit var countdownText: TextView
    private lateinit var btnCancel: Button
    private lateinit var btnSendSOS: Button
    private lateinit var btnSimulateCrash: Button

    private lateinit var locationClient: FusedLocationProviderClient
    private var timer: CountDownTimer? = null
    private var crashActive = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(R.layout.fragment_crash_alert, container, false)

        locationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        statusText = view.findViewById(R.id.statusText)
        countdownText = view.findViewById(R.id.countdownText)
        btnCancel = view.findViewById(R.id.btnCancel)
        btnSendSOS = view.findViewById(R.id.btnSendSOS)
        btnSimulateCrash = view.findViewById(R.id.btnSimulateCrash)

        btnCancel.setOnClickListener { cancelAlert() }
        btnSendSOS.setOnClickListener { sendSOS() }
        btnSimulateCrash.setOnClickListener { triggerCrash() }

        resetUI()
        return view
    }

    fun triggerCrash() {
        if (crashActive) return
        crashActive = true
        onCrashDetected()
    }

    private fun onCrashDetected() {
        statusText.text = "⚠️ CRASH DETECTED!"
        statusText.setTextColor(Color.RED)
        btnCancel.visibility = View.VISIBLE
        btnSendSOS.visibility = View.VISIBLE
        startCountdown()
    }

    private fun startCountdown() {
        timer = object : CountDownTimer(10_000, 1_000) {
            override fun onTick(millisUntilFinished: Long) {
                countdownText.text = "Sending SOS in ${millisUntilFinished / 1000}s"
            }
            override fun onFinish() {
                sendSOS()
            }
        }.start()
    }

    private fun cancelAlert() {
        timer?.cancel()
        crashActive = false
        resetUI()
    }

    private fun getEmergencyContacts(): List<String> {
        val prefs = requireContext()
            .getSharedPreferences("sos_prefs", android.content.Context.MODE_PRIVATE)

        val saved = prefs.getString("contacts", "")
        return if (saved.isNullOrEmpty()) emptyList() else saved.split(",")
    }

    private fun sendSOS() {
        timer?.cancel()
        crashActive = false

        val phoneNumbers = getEmergencyContacts()
        if (phoneNumbers.isEmpty()) {
            countdownText.text = "No emergency contacts set"
            return
        }

        val smsManager = android.telephony.SmsManager.getDefault()

        // 1️⃣ SEND SOS IMMEDIATELY (NO GPS DEPENDENCY)
        val baseMessage = """
        🚨 EMERGENCY ALERT 🚨
        Crash detected.
        Help needed immediately.
    """.trimIndent()

        for (number in phoneNumbers) {
            smsManager.sendTextMessage(number, null, baseMessage, null, null)
        }

        statusText.text = "🚨 SOS SENT"
        statusText.setTextColor(Color.RED)
        countdownText.text = "Trying to get location..."

        // 2️⃣ TRY TO FETCH LOCATION (OPTIONAL)
        val hasPermission =
            androidx.core.content.ContextCompat.checkSelfPermission(
                requireContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            countdownText.text = "Location permission not granted"
            return
        }

        locationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    val locationMessage =
                        "📍 Location:\nhttps://maps.google.com/?q=${location.latitude},${location.longitude}"

                    for (number in phoneNumbers) {
                        smsManager.sendTextMessage(number, null, locationMessage, null, null)
                    }

                    countdownText.text = "Location sent"
                } else {
                    countdownText.text = "Location unavailable"
                }
            }
            .addOnFailureListener {
                countdownText.text = "Location failed"
            }

        btnCancel.visibility = View.GONE
        btnSendSOS.visibility = View.GONE
    }



    private fun sendSms(numbers: List<String>, message: String) {
        val smsManager = SmsManager.getDefault()
        numbers.forEach {
            smsManager.sendTextMessage(it, null, message, null, null)
        }

        statusText.text = "🚨 SOS SENT"
        statusText.setTextColor(Color.RED)
        countdownText.text = "Emergency contacts notified"
        btnCancel.visibility = View.GONE
        btnSendSOS.visibility = View.GONE
    }

    private fun resetUI() {
        statusText.text = "Waiting for crash detection..."
        statusText.setTextColor(Color.GREEN)
        countdownText.text = ""
        btnCancel.visibility = View.GONE
        btnSendSOS.visibility = View.GONE
    }
}


