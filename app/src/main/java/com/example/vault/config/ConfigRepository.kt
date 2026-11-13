package com.example.vault.config

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.vault.model.AppConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persists AppConfig securely on Android using EncryptedSharedPreferences.
 */
class ConfigRepository(context: Context) {

    private val appContext = context.applicationContext
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            appContext.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
        }
    }

    fun load(): AppConfig {
        val raw = prefs.getString(KEY_CONFIG_JSON, null) ?: return AppConfig()
        return try {
            json.decodeFromString<AppConfig>(raw).normalized()
        } catch (_: Exception) {
            AppConfig()
        }
    }

    fun save(config: AppConfig) {
        val normalized = config.normalized()
        val serialized = json.encodeToString(normalized)
        prefs.edit().putString(KEY_CONFIG_JSON, serialized).apply()
    }

    companion object {
        private const val PREFS_FILE_NAME = "cloud_text_vault_secure_prefs"
        private const val KEY_CONFIG_JSON = "app_config_json"
    }
}
