package com.example.util

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

object AuthHelper {
    suspend fun getValidIdToken(forceRefresh: Boolean = false): String? {
        val user = FirebaseAuth.getInstance().currentUser ?: return null
        return try {
            val result = user.getIdToken(forceRefresh).await()
            result.token
        } catch (e: Exception) {
            android.util.Log.e("AuthHelper", "Failed to get Firebase token", e)
            null
        }
    }
}
