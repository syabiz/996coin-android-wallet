package com.coin996.wallet.ui.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.coin996.wallet.R
import com.coin996.wallet.data.repository.WalletRepository
import com.coin996.wallet.utils.SecurePreferences
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@SuppressLint("CustomSplashScreen")
@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    @Inject lateinit var walletRepository: WalletRepository
    @Inject lateinit var securePreferences: SecurePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        lifecycleScope.launch {
            delay(1000)

            if (walletRepository.isWalletSetup()) {
                if (securePreferences.hasPin()) {
                    showLockScreen()
                } else {
                    startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                    finish()
                }
            } else {
                startActivity(Intent(this@SplashActivity, SetupActivity::class.java))
                finish()
            }
        }
    }

    private fun showLockScreen() {
        if (securePreferences.isBiometricEnabled()) {
            val executor = ContextCompat.getMainExecutor(this)
            val biometricPrompt = BiometricPrompt(this, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                        finish()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        showPinDialog()
                    }
                })

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Login to 996coin")
                .setSubtitle("Use your biometric to unlock")
                .setNegativeButtonText("Use PIN")
                .build()

            biometricPrompt.authenticate(promptInfo)
        } else {
            showPinDialog()
        }
    }

    private fun showPinDialog() {
        val dialogBinding = com.coin996.wallet.databinding.DialogPinConfirmBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()

        dialogBinding.btnConfirm.setOnClickListener {
            val enteredPin = dialogBinding.etPin.text.toString()
            if (securePreferences.verifyPin(enteredPin)) {
                dialog.dismiss()
                startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                finish()
            } else {
                dialogBinding.tilPin.error = "Incorrect PIN"
            }
        }
        dialog.show()
    }
}
