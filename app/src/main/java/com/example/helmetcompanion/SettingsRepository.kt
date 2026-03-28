package com.example.helmetcompanion

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class SettingsRepository(
    private val preferencesStore: PreferencesStore
) {

    private val settingsLiveData = MutableLiveData(preferencesStore.getSettings())

    fun observeSettings(): LiveData<AppSettings> = settingsLiveData

    fun currentSettings(): AppSettings = settingsLiveData.value ?: preferencesStore.getSettings()

    fun updateSettings(settings: AppSettings) {
        preferencesStore.saveSettings(settings)
        settingsLiveData.postValue(settings)
    }
}
