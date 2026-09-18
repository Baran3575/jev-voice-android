package com.baran.jevvoice

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** API anahtarı SADECE telefonda saklanır (localstorage). Repoya/koda gömülmez. */
object SecureKeyStore {
    private const val PREFS = "jev_secure_prefs"
    private const val KEY_API = "typesafe_api_key"

    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveApiKey(context: Context, apiKey: String) {
        prefs(context).edit().putString(KEY_API, apiKey.trim()).apply()
    }

    fun getApiKey(context: Context): String? =
        prefs(context).getString(KEY_API, null)?.takeIf { it.isNotBlank() }

    fun hasApiKey(context: Context): Boolean = getApiKey(context) != null

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_API).apply()
    }
}
