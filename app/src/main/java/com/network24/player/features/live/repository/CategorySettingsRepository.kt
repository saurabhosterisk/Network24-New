package com.network24.player.features.live.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class CategorySettingsRepository(
    private val firestore: FirebaseFirestore
) {
    private fun doc(userId: String) =
        firestore.collection("user_category_settings").document(userId)

    suspend fun getDisabledCategoryIds(userId: String): Set<String> {
        return try {
            val snapshot = doc(userId).get().await()
            (snapshot.get("disabledIds") as? List<*>)
                ?.mapNotNull { it?.toString() }
                ?.toSet()
                ?: emptySet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    suspend fun setCategoryEnabled(userId: String, categoryId: String, enabled: Boolean) {
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
    }
}
