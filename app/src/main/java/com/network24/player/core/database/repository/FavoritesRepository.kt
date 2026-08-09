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

    suspend fun getFavoriteItemIds(userId: String, type: String): Set<String> {
        return try {
            val snapshot = doc(userId).get().await()
            val keys = snapshot.get("items") as? List<*> ?: emptyList<Any>()
            keys.mapNotNull { key ->
                val value = key?.toString() ?: return@mapNotNull null
                val parts = value.split(":", limit = 2)
                if (parts.getOrNull(0) == type) parts.getOrNull(1) else null
            }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    suspend fun syncFromCloud(userId: String) {
        try {
            val snapshot = doc(userId).get().await()
            if (!snapshot.exists()) return

            val keys = snapshot.get("items") as? List<String> ?: emptyList()
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

        favoritesDao.upsert(
            FavoriteEntity(
                key = key,
                itemType = type,
                itemId = itemId,
                createdAtMs = System.currentTimeMillis()
            )
        )

        val data = mapOf(
            "items" to FieldValue.arrayUnion(key),
            "updatedAtMs" to System.currentTimeMillis()
        )
        doc(userId).set(data, SetOptions.merge()).await()
    }

    suspend fun removeFavorite(userId: String, type: String, itemId: String) {
        val key = "$type:$itemId"

        favoritesDao.deleteByKey(key)

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
