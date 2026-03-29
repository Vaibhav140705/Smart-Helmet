package com.example.helmetcompanion

import android.net.Uri
import com.google.android.gms.maps.model.LatLng

data class EmergencyContact(
    val id: String,
    val displayName: String,
    val phoneNumber: String,
    val photoUri: String? = null,
    val fromDeviceContacts: Boolean = false
)

data class RiderProfile(
    val fullName: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val city: String = "",
    val dateOfBirth: String = "",
    val address: String = "",
    val bloodGroup: String = "",
    val riderPhotoUri: String? = null,
    val vehicleType: String = "",
    val vehicleModel: String = "",
    val vehicleRegistration: String = ""
) {
    fun isComplete(): Boolean {
        return fullName.isNotBlank() &&
                phoneNumber.isNotBlank() &&
                city.isNotBlank() &&
                dateOfBirth.isNotBlank() &&
                address.isNotBlank() &&
                bloodGroup.isNotBlank() &&
                vehicleType.isNotBlank() &&
                vehicleModel.isNotBlank() &&
                vehicleRegistration.isNotBlank()
    }
}

data class AppSettings(
    val pairedDeviceName: String = BuildConfig.HELMET_DEVICE_NAME,
    val sosCountdownSeconds: Int = 10,
    val includeLocationInSos: Boolean = true,
    val sosMessageTemplate: String = "Emergency alert from Helmet Companion. Crash detected and help is needed immediately."
)

enum class CrashAlertPhase {
    IDLE,
    CRASH_DETECTED,
    COUNTDOWN,
    SOS_SENDING,
    SOS_SENT,
    CANCELLED,
    FAILED
}

data class CrashAlertState(
    val phase: CrashAlertPhase = CrashAlertPhase.IDLE,
    val secondsRemaining: Int = 0,
    val message: String = "Helmet sensors are active and waiting for ride telemetry.",
    val details: String = "No crash events detected yet.",
    val activeEventId: String? = null
)

enum class BluetoothConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    PERMISSION_REQUIRED,
    DEVICE_NOT_FOUND,
    ERROR
}

data class BluetoothConnectionState(
    val status: BluetoothConnectionStatus = BluetoothConnectionStatus.DISCONNECTED,
    val deviceName: String = BuildConfig.HELMET_DEVICE_NAME,
    val details: String = "Helmet link is offline.",
    val lastMessage: String = ""
)

enum class HelmetEventType {
    CRASH,
    SOS,
    CANCELLED,
    HEARTBEAT,
    MESSAGE
}

data class HelmetEvent(
    val type: HelmetEventType,
    val rawMessage: String,
    val receivedAt: Long = System.currentTimeMillis()
)

enum class ManeuverType {
    LEFT,
    RIGHT,
    SLIGHT_LEFT,
    SLIGHT_RIGHT,
    STRAIGHT,
    UTURN,
    ARRIVE,
    UNKNOWN
}

data class RouteManeuver(
    val id: String,
    val instruction: String,
    val distanceMeters: Int,
    val durationText: String,
    val maneuverType: ManeuverType,
    val triggerPoint: LatLng,
    val triggerDistanceMeters: Float = 15f,
    val helmetCommand: String = maneuverType.toHelmetCommand()
)

data class RouteSession(
    val destinationName: String,
    val destinationLatLng: LatLng,
    val routePoints: List<LatLng>,
    val maneuvers: List<RouteManeuver>,
    val totalDistanceText: String,
    val totalDurationText: String,
    val isActive: Boolean = true
)

data class ContactDraft(
    val displayName: String,
    val phoneNumber: String,
    val photoUri: Uri? = null,
    val fromDeviceContacts: Boolean = false
)

fun ManeuverType.toHelmetCommand(): String = when (this) {
    ManeuverType.LEFT -> "L"
    ManeuverType.RIGHT -> "R"
    ManeuverType.SLIGHT_LEFT -> "L"
    ManeuverType.SLIGHT_RIGHT -> "R"
    ManeuverType.STRAIGHT -> "S"
    ManeuverType.UTURN -> "U"
    ManeuverType.ARRIVE -> "S"
    ManeuverType.UNKNOWN -> "S"
}