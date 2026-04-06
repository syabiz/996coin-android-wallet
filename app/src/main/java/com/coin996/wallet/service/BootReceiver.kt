package com.coin996.wallet.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.coin996.wallet.utils.SecurePreferences
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var securePreferences: SecurePreferences

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON") return

        // Only restart sync service if wallet is already setup
        if (securePreferences.isWalletSetup()) {
            val serviceIntent = Intent(context, SpvSyncService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
