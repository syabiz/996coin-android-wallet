package com.coin996.wallet.utils

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurePreferences @Inject constructor(private val context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "996coin_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // Wallet pin (Hashed)
    fun savePin(pin: String) {
        val salt = getDeviceSalt()
        val hashedPin = hashPin(pin, salt)
        prefs.edit().putString(KEY_PIN, hashedPin).apply()
    }

    fun verifyPin(enteredPin: String): Boolean {
        val savedHash = prefs.getString(KEY_PIN, null) ?: return false
        val salt = getDeviceSalt()
        return hashPin(enteredPin, salt) == savedHash
    }

    fun hasPin(): Boolean = prefs.contains(KEY_PIN)

    private fun getDeviceSalt(): String {
        return android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "default_salt"
    }

    private fun hashPin(pin: String, salt: String): String {
        val bytes = (pin + salt).toByteArray()
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }

    // Biometric enabled
    fun setBiometricEnabled(enabled: Boolean) =
        prefs.edit().putBoolean(KEY_BIOMETRIC, enabled).apply()
    fun isBiometricEnabled(): Boolean = prefs.getBoolean(KEY_BIOMETRIC, false)

    // Wallet setup complete flag
    fun setWalletSetup(done: Boolean) = prefs.edit().putBoolean(KEY_WALLET_SETUP, done).apply()
    fun isWalletSetup(): Boolean = prefs.getBoolean(KEY_WALLET_SETUP, false)

    // Currency preference
    fun setCurrency(currency: String) = prefs.edit().putString(KEY_CURRENCY, currency).apply()
    fun getCurrency(): String = prefs.getString(KEY_CURRENCY, "USD") ?: "USD"

    companion object {
        private const val KEY_PIN          = "pin"
        private const val KEY_BIOMETRIC    = "biometric_enabled"
        private const val KEY_WALLET_SETUP = "wallet_setup_done"
        private const val KEY_CURRENCY     = "currency"
    }
}
