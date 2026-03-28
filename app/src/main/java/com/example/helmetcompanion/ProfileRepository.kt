package com.example.helmetcompanion

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileRepository(
    private val preferencesStore: PreferencesStore
) {

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val profileLiveData = MutableLiveData(preferencesStore.getProfile())

    fun observeProfile(): LiveData<RiderProfile> = profileLiveData

    fun currentProfile(): RiderProfile = profileLiveData.value ?: RiderProfile()

    fun saveProfile(profile: RiderProfile, onComplete: ((Boolean) -> Unit)? = null) {
        preferencesStore.saveProfile(profile)
        profileLiveData.postValue(profile)

        val uid = auth.currentUser?.uid
        if (uid == null) {
            onComplete?.invoke(true)
            return
        }

        firestore.collection("users")
            .document(uid)
            .set(
                mapOf(
                    "fullName" to profile.fullName,
                    "email" to profile.email,
                    "phoneNumber" to profile.phoneNumber,
                    "city" to profile.city,
                    "dateOfBirth" to profile.dateOfBirth,
                    "address" to profile.address,
                    "bloodGroup" to profile.bloodGroup,
                    "riderPhotoUri" to profile.riderPhotoUri,
                    "vehicleType" to profile.vehicleType,
                    "vehicleModel" to profile.vehicleModel,
                    "vehicleRegistration" to profile.vehicleRegistration
                )
            )
            .addOnSuccessListener { onComplete?.invoke(true) }
            .addOnFailureListener { onComplete?.invoke(false) }
    }

    fun refreshFromCloud(onComplete: (() -> Unit)? = null) {
        val uid = auth.currentUser?.uid ?: run {
            onComplete?.invoke()
            return
        }
        firestore.collection("users").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val profile = RiderProfile(
                        fullName = doc.getString("fullName").orEmpty(),
                        email = doc.getString("email").orEmpty(),
                        phoneNumber = doc.getString("phoneNumber").orEmpty(),
                        city = doc.getString("city").orEmpty(),
                        dateOfBirth = doc.getString("dateOfBirth").orEmpty(),
                        address = doc.getString("address").orEmpty(),
                        bloodGroup = doc.getString("bloodGroup").orEmpty(),
                        riderPhotoUri = doc.getString("riderPhotoUri"),
                        vehicleType = doc.getString("vehicleType").orEmpty(),
                        vehicleModel = doc.getString("vehicleModel").orEmpty(),
                        vehicleRegistration = doc.getString("vehicleRegistration").orEmpty()
                    )
                    preferencesStore.saveProfile(profile)
                    profileLiveData.postValue(profile)
                }
                onComplete?.invoke()
            }
            .addOnFailureListener { onComplete?.invoke() }
    }
}
