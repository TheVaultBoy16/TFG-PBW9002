package com.example.myapplication.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class SessionManager(context: Context) {
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    
    private val sharedPreferences = EncryptedSharedPreferences.create(
        "session_prefs",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveSession(user: String, host: String, rsaKey: String, port: Int) {
        sharedPreferences.edit().apply {
            putString("user", user)
            putString("host", host)
            putString("rsaKey", rsaKey)
            putInt("port", port)
            putBoolean("isLoggedIn", true)
            apply()
        }
    }

    fun getSession(): SessionData? {
        if (!sharedPreferences.getBoolean("isLoggedIn", false)) return null
        
        return SessionData(
            user = sharedPreferences.getString("user", "") ?: "",
            host = sharedPreferences.getString("host", "") ?: "",
            rsaKey = sharedPreferences.getString("rsaKey", "") ?: "",
            port = sharedPreferences.getInt("port", 22)
        )
    }

    fun clearSession() {
        sharedPreferences.edit().clear().apply()
    }
}

data class SessionData(
    val user: String,
    val host: String,
    val rsaKey: String,
    val port: Int
)
