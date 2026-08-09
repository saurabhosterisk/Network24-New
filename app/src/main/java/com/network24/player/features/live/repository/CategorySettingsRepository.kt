package com.network24.player.features.live.repository

import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.network24.player.core.preferences.PreferenceManager
import kotlinx.coroutines.tasks.await

/**
 * Category settings are user-specific and are therefore cached locally.
 * Firebase is used as the cloud source of truth, but normal screen opens never
 * need to read the document again. The cache is refreshed on login.
 */
class CategorySettingsRepository(
    private val firestore: FirebaseFirestore,
    private val prefs: PreferenceManager = PreferenceManager(
        FirebaseApp.getInstance().applicationContext
    )
) {
    private fun doc(userId: String) =
        firestore.collection("user_category_settings").document(userId)

    suspend fun getDisabledCategoryIds(userId: String): Set<String> {
        prefs.getDisabledLiveCategoryIds()?.let { return it }

        return try {
            val snapshot = doc(userId).get().await()
            val ids = (snapshot.get("disabledIds") as? List<*>)
                ?.mapNotNull { it?.toString() }
                ?.toSet()
                ?: emptySet()
            prefs.setDisabledLiveCategoryIds(ids)
            ids
        } catch (_: Exception) {
            emptySet()
        }
    }

    /** Called after a successful login. This is the normal cloud refresh point. */
    suspend fun syncFromCloud(userId: String) {
        try {
            val snapshot = doc(userId).get().await()
            val ids = (snapshot.get("disabledIds") as? List<*>)
                ?.mapNotNull { it?.toString() }
                ?.toSet()
                ?: emptySet()
            prefs.setDisabledLiveCategoryIds(ids)
        } catch (_: Exception) {
            // Keep the previous local cache when offline.
        }
    }

    suspend fun setCategoryEnabled(userId: String, categoryId: String, enabled: Boolean) {
        val current = prefs.getDisabledLiveCategoryIds()?.toMutableSet() ?: mutableSetOf()
        if (enabled) current.remove(categoryId) else current.add(categoryId)

        val data = if (enabled) {
            mapOf(
                "disabledIds" to FieldValue.arrayRemove(categoryId),
                "updatedAtMs" to System.currentTimeMillis()
            )
        } else {
            mapOf(
                "disabledIds" to FieldValue.arrayUnion(categoryId),
                "updatedAtMs" to System.currentTimeMillis()
            )
        }
        doc(userId).set(data, SetOptions.merge()).await()
        // Update local state only after the cloud write succeeds.
        prefs.setDisabledLiveCategoryIds(current)
    }
}
