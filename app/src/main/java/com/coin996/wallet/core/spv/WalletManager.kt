package com.coin996.wallet.core.spv

import android.content.Context
import com.coin996.wallet.core.network.Coin996NetworkParams
import com.coin996.wallet.utils.SecurePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.bitcoinj.core.*
import org.bitcoinj.crypto.MnemonicCode
import org.bitcoinj.script.Script
import org.bitcoinj.store.SPVBlockStore
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.Wallet
import org.bitcoinj.wallet.WalletProtobufSerializer
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

// ─── Jenis alamat yang didukung 996coin ───────────────────────────────────────
enum class AddressType(val label: String, val description: String) {
    LEGACY(      "Legacy",       "Dimulai dengan N  (P2PKH)"),
    NESTED_SEGWIT("Nested SegWit","Dimulai dengan angka (P2SH-P2WPKH)"),
    NATIVE_SEGWIT("Native SegWit","Dimulai dengan 996 (Bech32 P2WPKH)")
}

// ─── State data classes ───────────────────────────────────────────────────────
data class WalletState(
    val isInitialized: Boolean = false,
    val isEncrypted: Boolean   = false,
    val balance: Coin          = Coin.ZERO,
    val pendingBalance: Coin   = Coin.ZERO,
    val syncProgress: Int      = 0,
    val isSyncing: Boolean     = false,
    val addresses: Map<AddressType, String> = emptyMap(),
    val activeAddressType: AddressType      = AddressType.LEGACY,
    val blockHeight: Int       = 0,
    val isWifImported: Boolean = false   // true jika wallet dari import WIF
)

data class TransactionItem(
    val txHash: String,
    val amountSatoshi: Long,
    val isSent: Boolean,
    val isConfirmed: Boolean,
    val confirmations: Int,
    val timestamp: Long,
    val toAddress: String?
)

// ─── Hasil operasi import WIF ─────────────────────────────────────────────────
sealed class WifImportResult {
    data class Success(
        val legacyAddress:      String,
        val nestedSegwitAddress: String,
        val nativeSegwitAddress: String
    ) : WifImportResult()
    data class Error(val message: String) : WifImportResult()
}

@Singleton
class WalletManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securePreferences: SecurePreferences
) {
    private val params = Coin996NetworkParams.get()

    private val walletFile get() = File(context.filesDir, "996coin.wallet")
    private val chainFile  get() = File(context.filesDir, "996coin.spvchain")

    private var wallet:     Wallet?      = null
    private var peerGroup:  PeerGroup?   = null
    private var blockStore: SPVBlockStore? = null
    private var blockChain: BlockChain?  = null

    private val _state = MutableStateFlow(WalletState())
    val state: StateFlow<WalletState> = _state

    private val _transactions = MutableStateFlow<List<TransactionItem>>(emptyList())
    val transactions: StateFlow<List<TransactionItem>> = _transactions

    // ── Tipe alamat yang aktif (bisa diganti user di Settings) ────────────────
    private var activeType: AddressType = AddressType.LEGACY

    // =========================================================================
    // WALLET CREATION — HD Mnemonic (BIP-32/39/44)
    // Untuk pengguna baru yang belum punya wallet Qt
    // =========================================================================

    suspend fun createNewWallet(): List<String> = withContext(Dispatchers.IO) {
        val seed = DeterministicSeed(
            java.security.SecureRandom(),
            null,
            "",
            Instant.now().epochSecond
        )
        // Buat wallet HD dengan script P2PKH sebagai default
        val w = Wallet.fromSeed(params, seed, Script.ScriptType.P2PKH)
        // Tambahkan watching untuk tipe SegWit juga
        addSegWitWatching(w)
        saveWalletSync(w)
        wallet = w
        updateState(w)
        seed.mnemonicCode ?: emptyList()
    }

    // =========================================================================
    // WALLET RESTORE — dari mnemonic 12 kata
    // =========================================================================

    suspend fun restoreFromMnemonic(
        words: List<String>,
        creationDate: Long? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val seed = DeterministicSeed(
                words,
                null,
                "",
                creationDate ?: (Instant.now().epochSecond - 60L * 60 * 24 * 365)
            )
            val w = Wallet.fromSeed(params, seed, Script.ScriptType.P2PKH)
            addSegWitWatching(w)
            chainFile.delete() // paksa rescan
            saveWalletSync(w)
            wallet = w
            updateState(w)
            true
        } catch (e: Exception) { false }
    }

    // =========================================================================
    // IMPORT WIF PRIVATE KEY
    // Kompatibel langsung dengan "dumprivkey" dari wallet Qt 996coin.
    // Satu WIF menghasilkan tiga alamat (Legacy, Nested SegWit, Native SegWit).
    // =========================================================================

    suspend fun importWifKey(wifString: String): WifImportResult =
        withContext(Dispatchers.IO) {
            try {
                // Decode WIF key
                val key = DumpedPrivateKey.fromBase58(params, wifString.trim()).key

                // Derive ketiga jenis alamat dari satu private key yang sama
                val legacyAddr = LegacyAddress.fromKey(params, key)
                    .toString()

                val nestedAddr = LegacyAddress.fromScriptHash(
                    params,
                    org.bitcoinj.script.ScriptBuilder.createP2WPKHOutputScript(key)
                        .let { SegwitAddress.fromKey(params, key).let {
                            // P2SH-P2WPKH: hash dari redeem script
                            Utils.sha256hash160(
                                org.bitcoinj.script.ScriptBuilder
                                    .createP2WPKHOutputScript(key).program
                            )
                        }}
                ).toString()

                val nativeAddr = SegwitAddress.fromKey(
                    params, key, Script.ScriptType.P2WPKH
                ).toString()

                // Buat atau load wallet, lalu import key
                val w = if (walletFile.exists()) {
                    FileInputStream(walletFile).use { fis ->
                        WalletProtobufSerializer().readWallet(params, null, fis)
                    }
                } else {
                    Wallet(params)
                }

                w.importKey(key)
                chainFile.delete() // rescan untuk temukan tx lama
                saveWalletSync(w)
                wallet = w
                updateState(w)

                WifImportResult.Success(
                    legacyAddress       = legacyAddr,
                    nestedSegwitAddress = nestedAddr,
                    nativeSegwitAddress = nativeAddr
                )
            } catch (e: WrongNetworkException) {
                WifImportResult.Error("WIF key bukan untuk jaringan 996coin")
            } catch (e: AddressFormatException) {
                WifImportResult.Error("Format WIF tidak valid")
            } catch (e: Exception) {
                WifImportResult.Error(e.message ?: "Gagal import key")
            }
        }

    // =========================================================================
    // DERIVE ADDRESS — dari wallet HD yang sudah ada
    // =========================================================================

    /**
     * Mengembalikan alamat receive untuk tipe yang diminta.
     * Wallet HD bisa generate ketiganya dari seed yang sama.
     */
    fun getReceiveAddress(type: AddressType = activeType): String? {
        val w = wallet ?: return null
        return try {
            when (type) {
                AddressType.LEGACY ->
                    w.currentReceiveAddress().toString()

                AddressType.NESTED_SEGWIT -> {
                    // P2SH-P2WPKH: alamat P2SH yang membungkus script P2WPKH
                    val key = w.currentReceiveKey()
                    val p2wpkhScript = org.bitcoinj.script.ScriptBuilder
                        .createP2WPKHOutputScript(key)
                    LegacyAddress.fromScriptHash(
                        params, Utils.sha256hash160(p2wpkhScript.program)
                    ).toString()
                }

                AddressType.NATIVE_SEGWIT -> {
                    // Bech32 P2WPKH — dimulai "996q..."
                    val key = w.currentReceiveKey()
                    SegwitAddress.fromKey(
                        params, key, Script.ScriptType.P2WPKH
                    ).toString()
                }
            }
        } catch (e: Exception) { null }
    }

    fun getAllAddresses(): Map<AddressType, String> {
        val w = wallet ?: return emptyMap()
        return AddressType.entries.associateWith { type ->
            getReceiveAddress(type) ?: ""
        }.filter { it.value.isNotEmpty() }
    }

    fun setActiveAddressType(type: AddressType) {
        activeType = type
        wallet?.let { updateState(it) }
    }

    fun generateNewAddress(type: AddressType = activeType): String? {
        val w = wallet ?: return null
        return try {
            when (type) {
                AddressType.LEGACY -> {
                    val addr = w.freshReceiveAddress()
                    saveWalletSync(w)
                    addr.toString()
                }
                AddressType.NESTED_SEGWIT -> {
                    val key = w.freshReceiveKey()
                    val script = org.bitcoinj.script.ScriptBuilder
                        .createP2WPKHOutputScript(key)
                    saveWalletSync(w)
                    LegacyAddress.fromScriptHash(
                        params, Utils.sha256hash160(script.program)
                    ).toString()
                }
                AddressType.NATIVE_SEGWIT -> {
                    val key = w.freshReceiveKey()
                    saveWalletSync(w)
                    SegwitAddress.fromKey(
                        params, key, Script.ScriptType.P2WPKH
                    ).toString()
                }
            }
        } catch (e: Exception) { null }
    }

    // =========================================================================
    // VALIDASI ALAMAT — cek semua tiga format
    // =========================================================================

    fun isValidAddress(address: String): Boolean {
        return try {
            Address.fromString(params, address.trim())
            true
        } catch (e: Exception) { false }
    }

    fun detectAddressType(address: String): AddressType? {
        return try {
            when (val addr = Address.fromString(params, address.trim())) {
                is LegacyAddress -> when (addr.version) {
                    params.addressHeader -> AddressType.LEGACY
                    params.p2shHeader    -> AddressType.NESTED_SEGWIT
                    else                 -> null
                }
                is SegwitAddress -> AddressType.NATIVE_SEGWIT
                else             -> null
            }
        } catch (e: Exception) { null }
    }

    // =========================================================================
    // LOAD WALLET
    // =========================================================================

    suspend fun loadWallet(): Boolean = withContext(Dispatchers.IO) {
        if (!walletFile.exists()) return@withContext false
        try {
            val w = FileInputStream(walletFile).use { fis ->
                WalletProtobufSerializer().readWallet(params, null, fis)
            }
            wallet = w
            updateState(w)
            true
        } catch (e: Exception) { false }
    }

    // =========================================================================
    // SPV SYNC
    // =========================================================================

    suspend fun startSync(progressListener: ((Int) -> Unit)? = null) =
        withContext(Dispatchers.IO) {
            val w = wallet ?: return@withContext
            val store = SPVBlockStore(params, chainFile)
            blockStore = store
            val chain = BlockChain(params, w, store)
            blockChain = chain
            val pg = PeerGroup(params, chain).apply {
                userAgent = "996coin-android", "1.0.0"
                addWallet(w)
                params.dnsSeeds?.forEach { addAddress(PeerAddress.lookup(params, it)) }
            }
            peerGroup = pg

            pg.addBlocksDownloadedEventListener { _, _, _, blocksLeft ->
                val total = (chain.bestChainHeight + blocksLeft).coerceAtLeast(1)
                val pct   = ((chain.bestChainHeight.toFloat() / total) * 100).toInt()
                progressListener?.invoke(pct)
                _state.value = _state.value.copy(
                    syncProgress = pct,
                    isSyncing    = blocksLeft > 0,
                    blockHeight  = chain.bestChainHeight
                )
                if (blocksLeft == 0) updateState(w)
            }

            pg.start()
            pg.downloadBlockChain()
        }

    fun stopSync() {
        peerGroup?.stop()
        blockStore?.close()
        peerGroup   = null
        blockStore  = null
    }

    // =========================================================================
    // SEND TRANSACTION
    // =========================================================================

    suspend fun sendCoins(
        toAddress: String,
        amountNns: Double,
        feePerKb: Coin = Transaction.DEFAULT_TX_FEE
    ): Result<String> = withContext(Dispatchers.IO) {
        val w  = wallet    ?: return@withContext Result.failure(Exception("Wallet belum dimuat"))
        val pg = peerGroup ?: return@withContext Result.failure(Exception("Belum terhubung ke jaringan"))
        try {
            val address = Address.fromString(params, toAddress.trim())
            val amount  = Coin.parseCoin(amountNns.toBigDecimal().toPlainString())
            val req     = Wallet.SendRequest.to(address, amount).also { it.feePerKb = feePerKb }
            val result  = w.sendCoins(pg, req)
            saveWalletSync(w)
            updateState(w)
            Result.success(result.tx.txId.toString())
        } catch (e: InsufficientMoneyException) {
            Result.failure(Exception("Saldo tidak cukup. Kurang: ${e.missing?.toFriendlyString()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // ENKRIPSI
    // =========================================================================

    suspend fun encryptWallet(password: String): Boolean = withContext(Dispatchers.IO) {
        try { wallet?.encrypt(password); wallet?.let { saveWalletSync(it) }; true }
        catch (e: Exception) { false }
    }

    suspend fun decryptWallet(password: String): Boolean = withContext(Dispatchers.IO) {
        try { wallet?.decrypt(password); wallet?.let { saveWalletSync(it) }; true }
        catch (e: Exception) { false }
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private fun addSegWitWatching(w: Wallet) {
        // bitcoinj 0.16 mendukung multiple script types dalam satu wallet HD
        try {
            w.addAndActivateHDChain(
                org.bitcoinj.wallet.DeterministicKeyChain.builder()
                    .seed(w.keyChainSeed)
                    .outputScriptType(Script.ScriptType.P2WPKH)
                    .build()
            )
        } catch (_: Exception) { /* fallback — gunakan Legacy saja */ }
    }

    private fun updateState(w: Wallet) {
        val allAddr = getAllAddresses()
        _state.value = _state.value.copy(
            isInitialized   = true,
            isEncrypted     = w.isEncrypted,
            balance         = w.getBalance(Wallet.BalanceType.AVAILABLE),
            pendingBalance  = w.getBalance(Wallet.BalanceType.ESTIMATED)
                .subtract(w.getBalance(Wallet.BalanceType.AVAILABLE)),
            addresses       = allAddr,
            activeAddressType = activeType,
            isWifImported   = w.importedKeys.isNotEmpty()
        )
        _transactions.value = w.transactionsByTime.map { tx ->
            val value = tx.getValue(w)
            TransactionItem(
                txHash       = tx.txId.toString(),
                amountSatoshi= value.abs().value,
                isSent       = value.isNegative,
                isConfirmed  = tx.confidence.confidenceType ==
                               TransactionConfidence.ConfidenceType.BUILDING,
                confirmations= tx.confidence.depthInBlocks,
                timestamp    = tx.updateTime?.time ?: 0L,
                toAddress    = tx.outputs.firstOrNull()
                               ?.scriptPubKey?.getToAddress(params)?.toString()
            )
        }
    }

    private fun saveWalletSync(w: Wallet) {
        FileOutputStream(walletFile).use { WalletProtobufSerializer().writeWallet(w, it) }
    }

    fun isWalletExists(): Boolean  = walletFile.exists()
    fun isConnected(): Boolean     = peerGroup?.isRunning == true
    fun getPeerCount(): Int        = peerGroup?.connectedPeers?.size ?: 0
    fun getMnemonicWords(): List<String>? = wallet?.keyChainSeed?.mnemonicCode
}
