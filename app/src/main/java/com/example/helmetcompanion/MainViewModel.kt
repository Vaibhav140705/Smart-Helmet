package com.example.helmetcompanion

import android.app.Application
import android.content.pm.PackageManager
import android.location.Location
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.maps.model.LatLng
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val container = AppContainer.from(application)
    private val triggerEngine = ManeuverTriggerEngine()

    val contacts = container.contactsRepository.observeContacts()
    val profile = container.profileRepository.observeProfile()
    val settings = container.settingsRepository.observeSettings()
    val bluetoothState = container.bluetoothRepository.observeConnectionState()
    val helmetEvent = container.bluetoothRepository.observeHelmetEvents()

    private val routeSessionMutable = MutableLiveData<RouteSession?>(null)
    private val routeMessageMutable = MutableLiveData("Search a destination to start turn-by-turn guidance.")
    private val routeErrorMutable = MutableLiveData<String?>(null)
    private val crashAlertMutable = MutableLiveData(CrashAlertState())
    private var activeCrashEventId: String? = null
    private var countdownRunnable: Runnable? = null
    private val handler = android.os.Handler(application.mainLooper)

    val routeSession: LiveData<RouteSession?> = routeSessionMutable
    val routeMessage: LiveData<String> = routeMessageMutable
    val routeError: LiveData<String?> = routeErrorMutable
    val crashAlertState: LiveData<CrashAlertState> = crashAlertMutable

    val onboardingRequired = MediatorLiveData<Boolean>().apply {
        val update: () -> Unit = {
            value = profile.value?.isComplete() != true
        }
        addSource(profile) { update() }
        update()
    }

    fun refreshCloudData() {
        container.profileRepository.refreshFromCloud()
        container.contactsRepository.refreshFromCloud()
    }

    fun connectHelmet() {
        container.bluetoothRepository.connect()
    }

    fun disconnectHelmet() {
        container.bluetoothRepository.disconnect()
    }

    fun consumeHelmetEvent() {
        container.bluetoothRepository.consumeEvent()
    }

    fun addEmergencyContact(draft: ContactDraft) {
        container.contactsRepository.addContact(draft)
    }

    fun removeEmergencyContact(contactId: String) {
        container.contactsRepository.removeContact(contactId)
    }

    fun saveProfile(profile: RiderProfile, onComplete: ((Boolean) -> Unit)? = null) {
        container.profileRepository.saveProfile(profile, onComplete)
    }

    fun updateSettings(settings: AppSettings) {
        container.settingsRepository.updateSettings(settings)
    }

    fun startCrashCountdown() {
        val settings = container.settingsRepository.currentSettings()
        val eventId = activeCrashEventId ?: UUID.randomUUID().toString().also { activeCrashEventId = it }
        scheduleCountdown(settings.sosCountdownSeconds, eventId)
    }

    fun handleHelmetEvent(event: HelmetEvent?) {
        when (event?.type) {
            HelmetEventType.CRASH -> {
                if (activeCrashEventId == null) {
                    activeCrashEventId = UUID.randomUUID().toString()
                    crashAlertMutable.postValue(
                        CrashAlertState(
                            phase = CrashAlertPhase.CRASH_DETECTED,
                            secondsRemaining = container.settingsRepository.currentSettings().sosCountdownSeconds,
                            message = "Crash detected by helmet sensors",
                            details = "A safety check is active. Tap \"I'm OK\" to cancel before SOS is sent.",
                            activeEventId = activeCrashEventId
                        )
                    )
                    startCrashCountdown()
                }
            }

            HelmetEventType.CANCELLED -> {
                activeCrashEventId = null
                stopCrashCountdown(
                    CrashAlertState(
                        phase = CrashAlertPhase.CANCELLED,
                        message = "Helmet cancelled the crash alert",
                        details = "The safety countdown was dismissed before SOS was sent."
                    )
                )
            }

            else -> Unit
        }
    }

    fun cancelCrashAlert() {
        container.bluetoothRepository.sendCommand("CANCEL")
        activeCrashEventId = null
        stopCrashCountdown(
            CrashAlertState(
                phase = CrashAlertPhase.CANCELLED,
                message = "Crash check cancelled",
                details = "No SOS was sent. Ride telemetry is back to standby."
            )
        )
    }

    fun resetCrashState() {
        activeCrashEventId = null
        stopCrashCountdown(CrashAlertState())
    }

    fun sendSos(onComplete: (Boolean, String) -> Unit) {
        countdownRunnable?.let(handler::removeCallbacks)
        if (
            ContextCompat.checkSelfPermission(
                getApplication(),
                android.Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            crashAlertMutable.postValue(
                CrashAlertState(
                    phase = CrashAlertPhase.FAILED,
                    message = "SMS permission not granted",
                    details = "Allow SMS permission in Android settings so Helmet Companion can send SOS alerts."
                )
            )
            onComplete(false, "SMS permission not granted")
            return
        }

        val contacts = container.contactsRepository.currentContacts()
        if (contacts.isEmpty()) {
            crashAlertMutable.postValue(
                CrashAlertState(
                    phase = CrashAlertPhase.FAILED,
                    message = "No emergency contacts added",
                    details = "Add at least one emergency contact before SOS can be sent."
                )
            )
            onComplete(false, "No emergency contacts added")
            return
        }

        crashAlertMutable.postValue(
            CrashAlertState(
                phase = CrashAlertPhase.SOS_SENDING,
                message = "Sending SOS alert",
                details = "Notifying emergency contacts with your current safety status."
            )
        )

        val settings = container.settingsRepository.currentSettings()
        val baseMessage = buildString {
            append(settings.sosMessageTemplate)
            val profile = container.profileRepository.currentProfile()
            if (profile.fullName.isNotBlank()) {
                append("\nRider: ${profile.fullName}")
            }
            if (profile.vehicleRegistration.isNotBlank()) {
                append("\nVehicle: ${profile.vehicleRegistration}")
            }
        }

        val sendMessages: (String) -> Unit = { locationSuffix ->
            try {
                val smsManager = SmsManager.getDefault()
                val baseText = baseMessage.trim()
                contacts.forEach { contact ->
                    val destination = contact.phoneNumber.filter {
                        it.isDigit() || it == '+' || it == '#'
                    }
                    if (destination.isBlank()) {
                        return@forEach
                    }
                    smsManager.sendTextMessage(destination, null, baseText, null, null)
                    if (locationSuffix.isNotBlank()) {
                        smsManager.sendTextMessage(destination, null, locationSuffix.trim(), null, null)
                    }
                }
                crashAlertMutable.postValue(
                    CrashAlertState(
                        phase = CrashAlertPhase.SOS_SENT,
                        message = "SOS sent successfully",
                        details = "Emergency contacts have been notified. Some phones do not show auto-sent SMS inside the sender's Messages app."
                    )
                )
                activeCrashEventId = null
                onComplete(true, "SOS sent")
            } catch (error: Exception) {
                crashAlertMutable.postValue(
                    CrashAlertState(
                        phase = CrashAlertPhase.FAILED,
                        message = "SOS failed",
                        details = error.message ?: "SMS delivery failed."
                    )
                )
                onComplete(false, error.message ?: "SMS delivery failed")
            }
        }

        if (!settings.includeLocationInSos) {
            sendMessages("")
            return
        }

        container.locationRepository.requestSingleFreshLocation { location ->
            val locationSuffix = if (location != null) {
                "Location: https://maps.google.com/?q=${location.latitude},${location.longitude}"
            } else {
                "Location unavailable."
            }
            sendMessages(locationSuffix)
        }
    }

    fun fetchRoute(
        destinationName: String,
        destinationLatLng: LatLng
    ) {
        routeErrorMutable.postValue(null)
        routeMessageMutable.postValue("Fetching the best route for helmet guidance...")
        container.locationRepository.requestSingleFreshLocation { location ->
            if (location == null) {
                routeErrorMutable.postValue("Current location unavailable. Enable GPS to start navigation.")
                return@requestSingleFreshLocation
            }
            container.navigationRepository.fetchRoute(
                origin = LatLng(location.latitude, location.longitude),
                destination = destinationLatLng,
                destinationName = destinationName
            ) { result ->
                result.onSuccess { route ->
                    triggerEngine.onRouteStarted()
                    routeSessionMutable.postValue(route)
                    routeMessageMutable.postValue("Route ready. Helmet cues will trigger before each turn.")
                }.onFailure { error ->
                    routeErrorMutable.postValue(error.message ?: "Unable to fetch route.")
                }
            }
        }
    }

    fun clearRoute() {
        routeSessionMutable.postValue(null)
        routeMessageMutable.postValue("Search a destination to start turn-by-turn guidance.")
        routeErrorMutable.postValue(null)
        triggerEngine.reset()
    }

    fun onRiderLocationUpdate(location: Location): RouteManeuver? {
        val route = routeSessionMutable.value ?: return null
        val maneuver = triggerEngine.onLocationUpdate(location, route) ?: return null
        container.bluetoothRepository.sendCommand(maneuver.helmetCommand)
        return maneuver
    }

    private fun scheduleCountdown(seconds: Int, eventId: String) {
        countdownRunnable?.let(handler::removeCallbacks)
        crashAlertMutable.postValue(
            CrashAlertState(
                phase = CrashAlertPhase.COUNTDOWN,
                secondsRemaining = seconds,
                message = "Crash detected. Safety countdown running.",
                details = "Confirm that you're okay, or SOS will be sent automatically.",
                activeEventId = eventId
            )
        )

        countdownRunnable = object : Runnable {
            var remaining = seconds
            override fun run() {
                if (activeCrashEventId != eventId) {
                    return
                }
                if (remaining <= 0) {
                    sendSos { _, _ -> }
                    return
                }
                crashAlertMutable.postValue(
                    CrashAlertState(
                        phase = CrashAlertPhase.COUNTDOWN,
                        secondsRemaining = remaining,
                        message = "Crash detected. Safety countdown running.",
                        details = "Automatic SOS in ${remaining}s unless cancelled.",
                        activeEventId = eventId
                    )
                )
                remaining -= 1
                handler.postDelayed(this, 1_000L)
            }
        }.also { handler.post(it) }
    }

    private fun stopCrashCountdown(state: CrashAlertState) {
        countdownRunnable?.let(handler::removeCallbacks)
        countdownRunnable = null
        crashAlertMutable.postValue(state)
    }
}

class MainViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
