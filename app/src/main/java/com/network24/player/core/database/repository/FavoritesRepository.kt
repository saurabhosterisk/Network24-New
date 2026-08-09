package com.network24.player.core.database.repository


import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.network24.player.core.database.dao.FavoritesDao
import com.network24.player.core.database.entity.FavoriteEntity
import kotlinx.coroutines.tasks.await

class FavoritesRepository(
    private val favoritesDao: FavoritesDao,
    private val firestore: FirebaseFirestore
) {
    private fun doc(userId: String) =
        firestore.collection("user_favorites").document(userId)

    suspend fun syncFromCloud(userId: String) {
        try {
            val snapshot = doc(userId).get().await()
            if (!snapshot.exists()) return

            val keys = snapshot.get("items") as? List<String> ?: emptyList()

            // local me add (duplicates REPLACE se handle ho jayenge)
            keys.forEach { key ->
                val parts = key.split(":", limit = 2)
                val type = parts.getOrNull(0) ?: return@forEach
                val itemId = parts.getOrNull(1) ?: return@forEach

                favoritesDao.upsert(
                    FavoriteEntity(
                        key = key,
                        itemType = type,
                        itemId = itemId,
                        createdAtMs = System.currentTimeMillis()
                    )
                )
            }
        } catch (_: Exception) {
            // offline / error => ignore
        }
    }

    suspend fun addFavorite(userId: String, type: String, itemId: String) {
        val key = "$type:$itemId"

        // 1) Room
        favoritesDao.upsert(
            FavoriteEntity(
                key = key,
                itemType = type,
                itemId = itemId,
                createdAtMs = System.currentTimeMillis()
            )
        )

        // 2) Firestore
        val data = mapOf(
            "items" to FieldValue.arrayUnion(key),
            "updatedAtMs" to System.currentTimeMillis()
        )
        doc(userId).set(data, SetOptions.merge()).await()
    }

    suspend fun removeFavorite(userId: String, type: String, itemId: String) {
        val key = "$type:$itemId"

        // 1) Room
        favoritesDao.deleteByKey(key)

        // 2) Firestore
        try {
            doc(userId).update(
                mapOf(
                    "items" to FieldValue.arrayRemove(key),
                    "updatedAtMs" to System.currentTimeMillis()
                )
            ).await()
        } catch (_: Exception) {
            // doc missing => ignore
        }
    }
}
