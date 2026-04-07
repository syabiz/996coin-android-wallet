package com.coin996.wallet.ui.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.coin996.wallet.R
import com.coin996.wallet.data.repository.WalletRepository
import com.coin996.wallet.utils.SecurePreferences
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
            delay(1200) // show splash for 1.2 seconds

            val intent = if (walletRepository.isWalletSetup()) {
                // Wallet exists → go to main (will require PIN/biometric)
                Intent(this@SplashActivity, MainActivity::class.java)
            } else {
                // First launch → go to setup
                Intent(this@SplashActivity, SetupActivity::class.java)
            }
            startActivity(intent)
            finish()
        }
    }
}
