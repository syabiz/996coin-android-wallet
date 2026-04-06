package com.coin996.wallet

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class WalletApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize bitcoinj logging
        org.bitcoinj.utils.BriefLogFormatter.initVerbose()
    }
}
