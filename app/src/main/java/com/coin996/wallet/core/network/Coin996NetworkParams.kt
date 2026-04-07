package com.coin996.wallet.core.network

import org.bitcoinj.core.*
import org.bitcoinj.net.discovery.HttpDiscovery
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.store.BlockStore
import java.net.InetSocketAddress

/**
 * 996coin Mainnet Network Parameters
 * Mewarisi MainNetParams untuk menghindari masalah akses konstruktor Block.
 */
class Coin996NetworkParams : MainNetParams() {

    init {
        id = ID_996COIN_MAINNET
        addressHeader = 53
        p2shHeader = 18
        dumpedPrivateKeyHeader = 128
        segwitAddressHrp = "996"
        packetMagic = 0x996c0133L
        port = 49969
        dnsSeeds = arrayOf("seed1.996coin.com", "seed2.996coin.com")
        
        // Removed addrSeeds because manual hardcoded seeds in WalletManager.startSync()
        // are already correctly handling the IP addresses.
        addrSeeds = intArrayOf()
        
        checkpoints[0] = Sha256Hash.wrap("48b00c93e8a20fb6b39f7d99b85066da2820cc3830ca223fc3e04d1a5b0dcbb7")
    }

    override fun checkDifficultyTransitions(storedPrev: StoredBlock, nextBlock: Block, blockStore: BlockStore) {
        // Blok PoS (setelah 500) tidak perlu cek difficulty PoW
        if (storedPrev.height >= 500) return
        try {
            super.checkDifficultyTransitions(storedPrev, nextBlock, blockStore)
        } catch (e: Exception) {
            if (storedPrev.height > 490) return
            throw e
        }
    }

    override fun getPaymentProtocolId(): String = "996coin"

    companion object {
        const val ID_996COIN_MAINNET = "com.coin996.wallet.mainnet"

        @Volatile private var instance: Coin996NetworkParams? = null
        @JvmStatic
        fun get(): Coin996NetworkParams = instance ?: synchronized(this) {
            instance ?: Coin996NetworkParams().also { instance = it }
        }
    }
}
