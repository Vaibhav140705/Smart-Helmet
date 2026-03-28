package com.example.helmetcompanion

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

class ContactsRepository(
    private val preferencesStore: PreferencesStore
) {

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val contactsLiveData = MutableLiveData(preferencesStore.getContacts())

    fun observeContacts(): LiveData<List<EmergencyContact>> = contactsLiveData

    fun currentContacts(): List<EmergencyContact> = contactsLiveData.value.orEmpty()

    fun addContact(draft: ContactDraft) {
        val updated = currentContacts().toMutableList().apply {
            add(
                EmergencyContact(
                    id = UUID.randomUUID().toString(),
                    displayName = draft.displayName.ifBlank { draft.phoneNumber },
                    phoneNumber = draft.phoneNumber,
                    photoUri = draft.photoUri?.toString(),
                    fromDeviceContacts = draft.fromDeviceContacts
                )
            )
        }
        save(updated)
    }

    fun removeContact(contactId: String) {
        save(currentContacts().filterNot { it.id == contactId })
    }

    fun refreshFromCloud(onComplete: (() -> Unit)? = null) {
        val uid = auth.currentUser?.uid ?: run {
            onComplete?.invoke()
            return
        }
        firestore.collection("users")
            .document(uid)
            .collection("emergencyContacts")
            .get()
            .addOnSuccessListener { snapshot ->
                val cloudContacts = snapshot.documents.mapNotNull { doc ->
                    val displayName = doc.getString("displayName").orEmpty()
                    val phoneNumber = doc.getString("phoneNumber").orEmpty()
                    if (phoneNumber.isBlank()) {
                        null
                    } else {
                        EmergencyContact(
                            id = doc.id,
                            displayName = displayName.ifBlank { phoneNumber },
                            phoneNumber = phoneNumber,
                            photoUri = doc.getString("photoUri"),
                            fromDeviceContacts = doc.getBoolean("fromDeviceContacts") ?: false
                        )
                    }
                }
                if (cloudContacts.isNotEmpty()) {
                    save(cloudContacts, syncToCloud = false)
                }
                onComplete?.invoke()
            }
            .addOnFailureListener {
                onComplete?.invoke()
            }
    }

    private fun save(contacts: List<EmergencyContact>, syncToCloud: Boolean = true) {
        preferencesStore.saveContacts(contacts)
        contactsLiveData.postValue(contacts)
        if (syncToCloud) {
            syncContactsToCloud(contacts)
        }
    }

    private fun syncContactsToCloud(contacts: List<EmergencyContact>) {
        val uid = auth.currentUser?.uid ?: return
        val collection = firestore.collection("users").document(uid).collection("emergencyContacts")
        collection.get().addOnSuccessListener { snapshot ->
            val activeIds = contacts.map { it.id }.toSet()
            snapshot.documents
                .filterNot { it.id in activeIds }
                .forEach { it.reference.delete() }

            contacts.forEach { contact ->
                collection.document(contact.id)
                    .set(
                        mapOf(
                            "displayName" to contact.displayName,
                            "phoneNumber" to contact.phoneNumber,
                            "photoUri" to contact.photoUri,
                            "fromDeviceContacts" to contact.fromDeviceContacts
                        )
                    )
            }
        }
    }
}
