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
import org.bitcoinj.core.listeners.PeerConnectedEventListener
import org.bitcoinj.core.listeners.PeerDisconnectedEventListener
import org.bitcoinj.script.Script
import org.bitcoinj.store.SPVBlockStore
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.Wallet
import org.bitcoinj.wallet.WalletProtobufSerializer
import org.bitcoinj.wallet.SendRequest
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

// Jenis alamat yang didukung 996coin
enum class AddressType(val label: String, val description: String) {
    LEGACY(      "Legacy",       "Dimulai dengan N (P2PKH)"),
    NESTED_SEGWIT("Nested SegWit","Dimulai dengan 8 (P2SH-P2WPKH)"),
    NATIVE_SEGWIT("Native SegWit","Dimulai dengan 996 (Bech32 P2WPKH)")
}

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
    val isWifImported: Boolean = false,
    val peerCount: Int         = 0
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
    // Bitcoinj Context
    private val bjContext = org.bitcoinj.core.Context(params)
    
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

    private var activeType: AddressType = AddressType.LEGACY

    suspend fun createNewWallet(): String? = withContext(Dispatchers.IO) {
        val w = Wallet.createDeterministic(bjContext, Script.ScriptType.P2PKH)
        addSegWitWatching(w)
        wallet = w
        updateState(w)
        getReceiveAddress(AddressType.LEGACY)
    }

    suspend fun encryptWallet(pin: String) = withContext(Dispatchers.IO) {
        val w = wallet ?: return@withContext
        if (!w.isEncrypted) {
            w.encrypt(pin)
            saveWalletSync(w)
            updateState(w)
        }
    }

    suspend fun decryptWallet(pin: String): Boolean = withContext(Dispatchers.IO) {
        val w = wallet ?: return@withContext false
        try {
            if (w.isEncrypted) {
                w.decrypt(pin)
                return@withContext true
            }
            true
        } catch (e: Exception) { false }
    }

    suspend fun restoreFromMnemonic(
        words: List<String>,
        creationTimeSec: Long? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Jan 01 2020 as a safe default if no time is provided, or 0L for full scan
            val seed = DeterministicSeed(words, null, "", creationTimeSec ?: 0L)
            val w = Wallet.fromSeed(params, seed, Script.ScriptType.P2PKH)
            addSegWitWatching(w)
            chainFile.delete()
            saveWalletSync(w)
            wallet = w
            updateState(w)
            true
        } catch (e: Exception) { false }
    }

    suspend fun importWifKey(wifString: String): WifImportResult =
        withContext(Dispatchers.IO) {
            try {
                val key = DumpedPrivateKey.fromBase58(params, wifString.trim()).key
                key.creationTimeSeconds = 0 // Set to 0 to trigger full rescan for this key

                val legacyAddr = LegacyAddress.fromKey(params, key).toString()
                val nativeAddr = SegwitAddress.fromKey(params, key, Script.ScriptType.P2WPKH).toString()
                val p2wpkhScript = org.bitcoinj.script.ScriptBuilder.createP2WPKHOutputScript(key)
                val nestedAddr = LegacyAddress.fromScriptHash(params, Utils.sha256hash160(p2wpkhScript.program)).toString()

                val serializer = WalletProtobufSerializer()
                val w = if (walletFile.exists()) {
                    FileInputStream(walletFile).use { fis -> serializer.readWallet(fis) }
                } else {
                    Wallet.createDeterministic(bjContext, Script.ScriptType.P2PKH)
                }

                // Stop sync before modifying files
                val wasSyncing = peerGroup?.isRunning == true
                if (wasSyncing) stopSync()

                w.importKey(key)
                if (chainFile.exists()) chainFile.delete()
                
                saveWalletSync(w)
                wallet = w
                updateState(w)

                // Restart sync if it was running
                if (wasSyncing) startSync()

                WifImportResult.Success(legacyAddr, nestedAddr, nativeAddr)
            } catch (e: Exception) {
                WifImportResult.Error(e.message ?: "Gagal import key")
            }
        }

    fun getReceiveAddress(type: AddressType = activeType): String? {
        val w = wallet ?: return null
        // Prefer imported keys (WIF) over HD keys if they exist to match QT behavior
        val key = w.importedKeys.firstOrNull() ?: w.currentReceiveKey()
        return try {
            when (type) {
                AddressType.LEGACY -> LegacyAddress.fromKey(params, key).toString()
                AddressType.NESTED_SEGWIT -> {
                    val script = org.bitcoinj.script.ScriptBuilder.createP2WPKHOutputScript(key)
                    LegacyAddress.fromScriptHash(params, Utils.sha256hash160(script.program)).toString()
                }
                AddressType.NATIVE_SEGWIT -> {
                    SegwitAddress.fromKey(params, key, Script.ScriptType.P2WPKH).toString()
                }
            }
        } catch (e: Exception) { null }
    }

    fun getAllAddresses(): Map<AddressType, String> {
        val w = wallet ?: return emptyMap()
        return AddressType.entries.associateWith { getReceiveAddress(it) ?: "" }.filter { it.value.isNotEmpty() }
    }

    fun setActiveAddressType(type: AddressType) {
        activeType = type
        wallet?.let { updateState(it) }
    }

    fun generateNewAddress(type: AddressType = activeType): String? {
        val w = wallet ?: return null
        return try {
            val addr = when (type) {
                AddressType.LEGACY -> w.freshReceiveAddress().toString()
                AddressType.NESTED_SEGWIT -> {
                    val key = w.freshReceiveKey()
                    val script = org.bitcoinj.script.ScriptBuilder.createP2WPKHOutputScript(key)
                    LegacyAddress.fromScriptHash(params, Utils.sha256hash160(script.program)).toString()
                }
                AddressType.NATIVE_SEGWIT -> {
                    val key = w.freshReceiveKey()
                    SegwitAddress.fromKey(params, key, Script.ScriptType.P2WPKH).toString()
                }
            }
            saveWalletSync(w)
            addr
        } catch (e: Exception) { null }
    }

    suspend fun loadWallet(): Boolean = withContext(Dispatchers.IO) {
        if (!walletFile.exists()) return@withContext false
        try {
            val w = FileInputStream(walletFile).use { fis ->
                WalletProtobufSerializer().readWallet(fis)
            }
            wallet = w
            updateState(w)
            true
        } catch (e: Exception) { false }
    }

    suspend fun startSync(progressListener: ((Int) -> Unit)? = null) =
        withContext(Dispatchers.IO) {
            val w = wallet ?: return@withContext
            if (peerGroup != null && peerGroup!!.isRunning) return@withContext

            blockStore = SPVBlockStore(params, chainFile)
            blockChain = BlockChain(params, w, blockStore)
            peerGroup = PeerGroup(params, blockChain).apply {
                // Add more stable nodes for 996coin
                val nodes = listOf(
                    "5.61.91.210", 
                    "80.190.82.162", 
                    "93.133.218.143", 
                    "109.241.180.151",
                    "161.97.100.100",
                    "194.163.150.150"
                )
                nodes.forEach { ip ->
                    try {
                        addAddress(PeerAddress(params, InetAddress.getByName(ip), params.getPort()))
                    } catch (_: Exception) {}
                }

                // Increase max connections to improve stability
                maxConnections = 12
                setUserAgent("996coinWalletAndroid", "1.0")

                addConnectedEventListener { _, _ -> updateState(w) }
                addDisconnectedEventListener { _, _ -> updateState(w) }
            }

            peerGroup?.addBlocksDownloadedEventListener { _, _, _, blocksLeft ->
                val total = (blockChain!!.bestChainHeight + blocksLeft).coerceAtLeast(1)
                val pct   = ((blockChain!!.bestChainHeight.toFloat() / total) * 100).toInt()
                progressListener?.invoke(pct)
                _state.value = _state.value.copy(syncProgress = pct, isSyncing = blocksLeft > 0, blockHeight = blockChain!!.bestChainHeight)
                if (blocksLeft == 0) updateState(w)
            }
            peerGroup?.start()
            peerGroup?.downloadBlockChain()
        }

    fun stopSync() {
        peerGroup?.stop(); blockStore?.close()
        peerGroup = null; blockStore = null
    }

    suspend fun sendCoins(
        toAddress: String,
        amountNns: Double,
        feePerKb: Coin = Coin.valueOf(10000) // Default 0.0001 NNS per KB
    ): Result<String> = withContext(Dispatchers.IO) {
        val w = wallet ?: return@withContext Result.failure(Exception("Wallet not loaded"))
        val pg = peerGroup ?: return@withContext Result.failure(Exception("Network not connected"))
        try {
            val address = Address.fromString(params, toAddress.trim())
            val amount  = Coin.parseCoin(amountNns.toBigDecimal().toPlainString())
            val req     = SendRequest.to(address, amount).apply { this.feePerKb = feePerKb }
            w.completeTx(req)
            pg.broadcastTransaction(req.tx)
            saveWalletSync(w)
            updateState(w)
            Result.success(req.tx.txId.toString())
        } catch (e: Exception) { Result.failure(e) }
    }

    private fun addSegWitWatching(w: Wallet) {
        try {
            if (w.activeKeyChain.outputScriptType != Script.ScriptType.P2WPKH) {
                w.addAndActivateHDChain(org.bitcoinj.wallet.DeterministicKeyChain.builder()
                    .seed(w.keyChainSeed).outputScriptType(Script.ScriptType.P2WPKH).build())
            }
        } catch (_: Exception) {}
    }

    private fun updateState(w: Wallet) {
        _state.value = _state.value.copy(
            isInitialized = true, isEncrypted = w.isEncrypted,
            balance = w.getBalance(Wallet.BalanceType.AVAILABLE),
            pendingBalance = w.getBalance(Wallet.BalanceType.ESTIMATED).subtract(w.getBalance(Wallet.BalanceType.AVAILABLE)),
            addresses = getAllAddresses(), activeAddressType = activeType,
            isWifImported = w.importedKeys.isNotEmpty(),
            peerCount = peerGroup?.connectedPeers?.size ?: 0
        )
        _transactions.value = w.getTransactionsByTime().map { tx ->
            val value = tx.getValue(w)
            TransactionItem(
                txHash = tx.txId.toString(), amountSatoshi = kotlin.math.abs(value.value),
                isSent = value.isNegative, isConfirmed = tx.confidence.confidenceType == TransactionConfidence.ConfidenceType.BUILDING,
                confirmations = tx.confidence.depthInBlocks, timestamp = tx.updateTime?.time ?: 0L,
                toAddress = tx.outputs.firstOrNull()?.scriptPubKey?.getToAddress(params)?.toString()
            )
        }
    }

    @Synchronized
    private fun saveWalletSync(w: Wallet) {
        FileOutputStream(walletFile).use { WalletProtobufSerializer().writeWallet(w, it) }
    }

    /** Validasi alamat 996coin */
    fun isValidAddress(address: String): Boolean {
        return try {
            Address.fromString(params, address.trim())
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Deteksi jenis alamat yang dimasukkan */
    fun detectAddressType(address: String): AddressType? {
        return try {
            val addr = Address.fromString(params, address.trim())
            when {
                addr is SegwitAddress -> AddressType.NATIVE_SEGWIT
                addr is LegacyAddress -> {
                    // Di bitcoinj, P2SH (8...) dan P2PKH (N...) keduanya bisa berupa LegacyAddress
                    // namun p2shHeader membedakannya.
                    if (address.trim().startsWith("8")) AddressType.NESTED_SEGWIT
                    else AddressType.LEGACY
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun isWalletExists() = walletFile.exists()
    fun isConnected() = peerGroup?.isRunning == true
    fun getPeerCount() = peerGroup?.connectedPeers?.size ?: 0
    fun getMnemonicWords() = wallet?.keyChainSeed?.mnemonicCode

    /** BUG 3 Fix: Implementasi Rescan Blockchain */
    suspend fun rescanBlockchain() = withContext(Dispatchers.IO) {
        val w = wallet ?: return@withContext
        stopSync()
        // Hapus file SPV chain agar mendownload ulang dari awal (atau dari checkpoint)
        if (chainFile.exists()) chainFile.delete()
        // Bersihkan transaksi lama di wallet untuk trigger re-discovery
        w.clearTransactions(0)
        saveWalletSync(w)
        updateState(w)
        startSync()
    }
}
