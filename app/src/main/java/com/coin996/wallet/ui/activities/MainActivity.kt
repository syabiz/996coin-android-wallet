package com.coin996.wallet.ui.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.coin996.wallet.R
import com.coin996.wallet.databinding.ActivityMainBinding
import com.coin996.wallet.service.SpvSyncService
import com.coin996.wallet.utils.parsePaymentUri
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup Navigation
        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHost.navController
        binding.bottomNavigation.setupWithNavController(navController)

        // Start SPV sync service
        startSpvService()

        // Handle 996coin: URI deep link
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val uri = intent.data?.toString() ?: return
        val payment = parsePaymentUri(uri) ?: return
        // Navigate to Send fragment with pre-filled data
        val bundle = Bundle().apply {
            putString("address", payment.address)
            payment.amount?.let { putDouble("amount", it) }
            payment.label?.let { putString("label", it) }
        }
        navController.navigate(R.id.sendFragment, bundle)
    }

    private fun startSpvService() {
        val intent = Intent(this, SpvSyncService::class.java)
        startForegroundService(intent)
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}
