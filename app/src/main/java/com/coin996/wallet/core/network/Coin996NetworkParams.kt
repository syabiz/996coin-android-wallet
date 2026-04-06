package com.coin996.wallet.core.network

import org.bitcoinj.core.Block
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.core.StoredBlock
import org.bitcoinj.core.VerificationException
import org.bitcoinj.params.AbstractBitcoinNetParams
import org.bitcoinj.store.BlockStore
import java.math.BigInteger

/**
 * 996coin Mainnet Network Parameters
 *
 * Mendukung tiga jenis alamat (sesuai wallet Qt 996coin):
 *
 *   1. Legacy P2PKH  — prefix byte 53  → alamat dimulai dengan 'N'
 *   2. Nested SegWit P2SH-P2WPKH — prefix byte 18  → dimulai angka
 *   3. Native SegWit Bech32 P2WPKH — HRP "996"     → dimulai "996..."
 *
 * Import key: WIF (Wallet Import Format) dengan prefix byte 128,
 * kompatibel langsung dengan wallet Qt 996coin.
 */
class Coin996NetworkParams private constructor() : AbstractBitcoinNetParams() {

    init {
        id = ID_996COIN_MAINNET

        // ── Address prefixes ──────────────────────────────────────────────
        // Legacy P2PKH: byte 53 → Base58Check → alamat 'N...'
        addressHeader = 53

        // P2SH (Nested SegWit P2SH-P2WPKH): byte 18
        p2shHeader = 18

        // WIF private key prefix: byte 128 (sama dengan Bitcoin mainnet)
        // Kompatibel langsung dengan "dumprivkey" dari wallet Qt 996coin
        dumpedPrivateKeyHeader = 128

        // ── Native SegWit (Bech32) ────────────────────────────────────────
        // HRP "996" → alamat native segwit dimulai "996q..." atau "996p..."
        segwitAddressHrp = "996"

        // ── Network magic (message start) ─────────────────────────────────
        // Dari chainparams.cpp: 0x99, 0x6c, 0x01, 0x33
        packetMagic = 0x996c0133L

        // ── Ports ─────────────────────────────────────────────────────────
        port = 49969  // Mainnet P2P port

        // ── DNS seeds ─────────────────────────────────────────────────────
        // Isi dengan seed node aktual dari project 996coin
        dnsSeeds = arrayOf(
            "seed1.996coin.com",
            "seed2.996coin.com",
            "seed3.996coin.com"
        )

        // ── Timing ────────────────────────────────────────────────────────
        // Target block spacing: 3 menit = 180 detik
        // Target timespan: 10 blok × 3 menit = 30 menit
        TARGET_TIMESPAN = 10 * 3 * 60
        TARGET_SPACING  = 3 * 60

        // ── Supply ────────────────────────────────────────────────────────
        maxMoney = org.bitcoinj.core.Coin.COIN.multiply(1_000_000_000L)

        // ── Coinbase maturity ─────────────────────────────────────────────
        spendableCoinbaseDepth = 100

        // ── PoW difficulty ────────────────────────────────────────────────
        proofOfWorkLimit = BigInteger(
            "00000000ffffffffffffffffffffffffffffffffffffffffffffffffffffffff", 16
        )

        genesisBlock = createGenesis()
    }

    private fun createGenesis(): Block {
        val genesis = Block(this, Block.BLOCK_VERSION_GENESIS)
        // Genesis message: "Why are we so rich? Because 996!"
        // Hash: 48b00c93e8a20fb6b39f7d99b85066da2820cc3830ca223fc3e04d1a5b0dcbb7
        // Untuk SPV kita hanya butuh hash untuk checkpoint, tidak perlu
        // merekonstruksi genesis block secara penuh.
        return genesis
    }

    override fun checkDifficultyTransitions(
        storedPrev: StoredBlock,
        nextBlock: Block,
        blockStore: BlockStore
    ) {
        // Blok 0–500: PoW aktif, cek difficulty normal
        // Blok 501+: PoS, lewati cek PoW difficulty
        if (storedPrev.height >= LAST_POW_BLOCK) return
        super.checkDifficultyTransitions(storedPrev, nextBlock, blockStore)
    }

    override fun getPaymentProtocolId(): String = "996coin"

    companion object {
        const val ID_996COIN_MAINNET = "com.coin996.wallet.mainnet"

        // Blok terakhir PoW sebelum beralih ke PoS
        const val LAST_POW_BLOCK = 500

        // Tinggi blok cold staking mulai aktif
        const val COLD_STAKE_ENABLE_HEIGHT = 260_000

        // MPoS: 10 penerima reward per blok selama fase MPoS
        const val MPOS_REWARD_RECIPIENTS = 10

        // Batas efektif stake weight per UTXO
        const val MAX_STAKE_WEIGHT_COINS = 125_000L

        @Volatile private var instance: Coin996NetworkParams? = null

        @JvmStatic
        fun get(): Coin996NetworkParams = instance ?: synchronized(this) {
            instance ?: Coin996NetworkParams().also { instance = it }
        }
    }
}
