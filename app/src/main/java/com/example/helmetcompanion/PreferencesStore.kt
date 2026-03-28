package com.example.helmetcompanion

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class PreferencesStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("helmet_companion_store", Context.MODE_PRIVATE)

    fun getSettings(): AppSettings {
        val storedDeviceName = prefs.getString(KEY_DEVICE_NAME, null).orEmpty().trim()
        val resolvedDeviceName = when (storedDeviceName) {
            "", "Helmet Companion", "ESP32_HELMET" -> BuildConfig.HELMET_DEVICE_NAME.ifBlank { "ESP_32_Bl" }
            else -> storedDeviceName
        }
        return AppSettings(
            pairedDeviceName = resolvedDeviceName,
            sosCountdownSeconds = prefs.getInt(KEY_COUNTDOWN, 10).coerceIn(5, 30),
            includeLocationInSos = prefs.getBoolean(KEY_INCLUDE_LOCATION, true),
            sosMessageTemplate = prefs.getString(
                KEY_SOS_TEMPLATE,
                "Emergency alert from Helmet Companion. Crash detected and help is needed immediately."
            ).orEmpty()
        )
    }

    fun saveSettings(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_DEVICE_NAME, settings.pairedDeviceName.ifBlank { "ESP_32_Bl" })
            .putInt(KEY_COUNTDOWN, settings.sosCountdownSeconds)
            .putBoolean(KEY_INCLUDE_LOCATION, settings.includeLocationInSos)
            .putString(KEY_SOS_TEMPLATE, settings.sosMessageTemplate)
            .apply()
    }

    fun getContacts(): List<EmergencyContact> {
        val raw = prefs.getString(KEY_CONTACTS, "[]").orEmpty()
        val jsonArray = JSONArray(raw)
        return buildList {
            for (index in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(index)
                add(
                    EmergencyContact(
                        id = item.optString("id", UUID.randomUUID().toString()),
                        displayName = item.optString("displayName"),
                        phoneNumber = item.optString("phoneNumber"),
                        photoUri = item.optString("photoUri").ifBlank { null },
                        fromDeviceContacts = item.optBoolean("fromDeviceContacts", false)
                    )
                )
            }
        }
    }

    fun saveContacts(contacts: List<EmergencyContact>) {
        val jsonArray = JSONArray()
        contacts.forEach { contact ->
            jsonArray.put(
                JSONObject()
                    .put("id", contact.id)
                    .put("displayName", contact.displayName)
                    .put("phoneNumber", contact.phoneNumber)
                    .put("photoUri", contact.photoUri ?: "")
                    .put("fromDeviceContacts", contact.fromDeviceContacts)
            )
        }
        prefs.edit().putString(KEY_CONTACTS, jsonArray.toString()).apply()
    }

    fun getProfile(): RiderProfile {
        val raw = prefs.getString(KEY_PROFILE, null) ?: return RiderProfile()
        val json = JSONObject(raw)
        return RiderProfile(
            fullName = json.optString("fullName"),
            email = json.optString("email"),
            phoneNumber = json.optString("phoneNumber"),
            city = json.optString("city"),
            dateOfBirth = json.optString("dateOfBirth"),
            address = json.optString("address"),
            bloodGroup = json.optString("bloodGroup"),
            riderPhotoUri = json.optString("riderPhotoUri").ifBlank { null },
            vehicleType = json.optString("vehicleType"),
            vehicleModel = json.optString("vehicleModel"),
            vehicleRegistration = json.optString("vehicleRegistration")
        )
    }

    fun saveProfile(profile: RiderProfile) {
        val json = JSONObject()
            .put("fullName", profile.fullName)
            .put("email", profile.email)
            .put("phoneNumber", profile.phoneNumber)
            .put("city", profile.city)
            .put("dateOfBirth", profile.dateOfBirth)
            .put("address", profile.address)
            .put("bloodGroup", profile.bloodGroup)
            .put("riderPhotoUri", profile.riderPhotoUri ?: "")
            .put("vehicleType", profile.vehicleType)
            .put("vehicleModel", profile.vehicleModel)
            .put("vehicleRegistration", profile.vehicleRegistration)
        prefs.edit().putString(KEY_PROFILE, json.toString()).apply()
    }

    companion object {
        private const val KEY_SETTINGS = "settings"
        private const val KEY_CONTACTS = "contacts"
        private const val KEY_PROFILE = "profile"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_COUNTDOWN = "countdown"
        private const val KEY_INCLUDE_LOCATION = "include_location"
        private const val KEY_SOS_TEMPLATE = "sos_template"
    }
}
