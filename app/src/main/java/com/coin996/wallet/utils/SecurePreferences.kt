package com.coin996.wallet.utils

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurePreferences @Inject constructor(context: Context) {

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

    // Wallet pin
    fun savePin(pin: String) = prefs.edit().putString(KEY_PIN, pin).apply()
    fun getPin(): String? = prefs.getString(KEY_PIN, null)
    fun hasPin(): Boolean = prefs.contains(KEY_PIN)

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
