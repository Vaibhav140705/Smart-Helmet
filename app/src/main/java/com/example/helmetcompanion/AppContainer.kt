package com.example.helmetcompanion

import android.content.Context

class AppContainer private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val preferencesStore = PreferencesStore(appContext)

    val settingsRepository by lazy { SettingsRepository(preferencesStore) }
    val contactsRepository by lazy { ContactsRepository(preferencesStore) }
    val profileRepository by lazy { ProfileRepository(preferencesStore) }
    val bluetoothRepository by lazy { BluetoothRepository(appContext, settingsRepository) }
    val locationRepository by lazy { LocationRepository(appContext) }
    val navigationRepository by lazy { NavigationRepository() }

    companion object {
        @Volatile
        private var instance: AppContainer? = null

        fun from(context: Context): AppContainer {
            return instance ?: synchronized(this) {
                instance ?: AppContainer(context).also { instance = it }
            }
        }
    }
}
