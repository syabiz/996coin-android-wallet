package com.coin996.wallet.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coin996.wallet.core.spv.AddressType
import com.coin996.wallet.core.spv.TransactionItem
import com.coin996.wallet.core.spv.WalletState
import com.coin996.wallet.core.spv.WifImportResult
import com.coin996.wallet.data.network.PriceData
import com.coin996.wallet.data.repository.PriceRepository
import com.coin996.wallet.data.repository.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── HomeViewModel ─────────────────────────────────────────────────────────────

@HiltViewModel
class HomeViewModel @Inject constructor(
    val walletRepository: WalletRepository,
    private val priceRepository: PriceRepository
) : ViewModel() {

    val walletState: StateFlow<WalletState>          = walletRepository.walletState
    val transactions: StateFlow<List<TransactionItem>> = walletRepository.transactions

    private val _priceData    = MutableStateFlow<PriceData?>(null)
    val priceData: StateFlow<PriceData?> = _priceData

    private val _priceHistory = MutableStateFlow<List<Pair<Long, Double>>>(emptyList())
    val priceHistory: StateFlow<List<Pair<Long, Double>>> = _priceHistory

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init { loadPrice() }

    fun loadPrice() {
        viewModelScope.launch {
            _isLoading.value = true
            priceRepository.getPrice().onSuccess      { _priceData.value    = it }
            priceRepository.getPriceHistory().onSuccess { _priceHistory.value = it }
            _isLoading.value = false
        }
    }

    fun refreshAll() { loadPrice() }

    fun setAddressType(type: AddressType) {
        walletRepository.walletManager.setActiveAddressType(type)
    }
}

// ─── SendViewModel ─────────────────────────────────────────────────────────────

@HiltViewModel
class SendViewModel @Inject constructor(
    private val walletRepository: WalletRepository,
    private val priceRepository: PriceRepository
) : ViewModel() {

    val walletState: StateFlow<WalletState> = walletRepository.walletState

    private val _sendResult = MutableStateFlow<SendResult?>(null)
    val sendResult: StateFlow<SendResult?> = _sendResult

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending

    private val _priceUsd = MutableStateFlow(0.0)
    val priceUsd: StateFlow<Double> = _priceUsd

    init {
        viewModelScope.launch {
            priceRepository.getPrice().onSuccess { _priceUsd.value = it.priceUsd }
        }
    }

    fun send(toAddress: String, amountNns: Double) {
        viewModelScope.launch {
            _isSending.value = true
            val result = walletRepository.walletManager.sendCoins(toAddress, amountNns)
            _sendResult.value = result.fold(
                onSuccess = { txId -> SendResult.Success(txId) },
                onFailure = { e   -> SendResult.Error(e.message ?: "Error tidak diketahui") }
            )
            _isSending.value = false
        }
    }

    /** Validasi alamat — mendukung Legacy, Nested SegWit, dan Native SegWit */
    fun isValidAddress(address: String): Boolean =
        walletRepository.walletManager.isValidAddress(address)

    /** Deteksi jenis alamat yang di-paste/scan */
    fun detectAddressType(address: String): AddressType? =
        walletRepository.walletManager.detectAddressType(address)

    fun clearResult() { _sendResult.value = null }
}

sealed class SendResult {
    data class Success(val txId: String)    : SendResult()
    data class Error(val message: String)   : SendResult()
}

// ─── ReceiveViewModel ─────────────────────────────────────────────────────────

@HiltViewModel
class ReceiveViewModel @Inject constructor(
    private val walletRepository: WalletRepository
) : ViewModel() {

    val walletState: StateFlow<WalletState> = walletRepository.walletState

    private val _selectedType = MutableStateFlow(AddressType.LEGACY)
    val selectedType: StateFlow<AddressType> = _selectedType

    private val _address = MutableStateFlow("")
    val address: StateFlow<String> = _address

    init { loadAddress(AddressType.LEGACY) }

    fun selectAddressType(type: AddressType) {
        _selectedType.value = type
        loadAddress(type)
    }

    fun loadAddress(type: AddressType = _selectedType.value) {
        _address.value = walletRepository.walletManager.getReceiveAddress(type) ?: ""
    }

    fun generateNewAddress() {
        _address.value =
            walletRepository.walletManager.generateNewAddress(_selectedType.value) ?: ""
    }

    fun getAllAddresses(): Map<AddressType, String> =
        walletRepository.walletManager.getAllAddresses()
}

// ─── SetupViewModel ────────────────────────────────────────────────────────────

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val walletRepository: WalletRepository
) : ViewModel() {

    private val _setupState = MutableStateFlow<SetupState>(SetupState.Idle)
    val setupState: StateFlow<SetupState> = _setupState

    private val _generatedWords = MutableStateFlow<List<String>>(emptyList())
    val generatedWords: StateFlow<List<String>> = _generatedWords

    fun createNewWallet() {
        viewModelScope.launch {
            _setupState.value = SetupState.Loading
            try {
                walletRepository.walletManager.createNewWallet()
                _setupState.value = SetupState.WalletRestored
            } catch (e: Exception) {
                _setupState.value = SetupState.Error(e.message ?: "Failed to create wallet")
            }
        }
    }

    fun restoreWallet(words: List<String>, creationDate: Long? = null) {
        viewModelScope.launch {
            _setupState.value = SetupState.Loading
            val success = walletRepository.restoreWallet(words, creationDate)
            _setupState.value = if (success) SetupState.WalletRestored
                                else SetupState.Error(
                                    "Seed phrase tidak valid. Periksa kembali ejaan tiap kata.")
        }
    }

    /**
     * Import WIF private key dari wallet Qt 996coin.
     * Menghasilkan tiga alamat dari satu private key yang sama.
     */
    fun importWifKey(wif: String) {
        viewModelScope.launch {
            _setupState.value = SetupState.Loading
            when (val result = walletRepository.walletManager.importWifKey(wif)) {
                is WifImportResult.Success -> {
                    _setupState.value = SetupState.WifImported(
                        legacyAddress       = result.legacyAddress,
                        nestedSegwitAddress = result.nestedSegwitAddress,
                        nativeSegwitAddress = result.nativeSegwitAddress
                    )
                }
                is WifImportResult.Error -> {
                    _setupState.value = SetupState.Error(result.message)
                }
            }
        }
    }
}

// ─── SetupState ───────────────────────────────────────────────────────────────

sealed class SetupState {
    object Idle           : SetupState()
    object Loading        : SetupState()
    object WalletRestored : SetupState()

    data class WalletCreated(val words: List<String>) : SetupState()

    /** Hasil import WIF — tiga alamat yang ter-derive dari satu private key */
    data class WifImported(
        val legacyAddress:       String,
        val nestedSegwitAddress: String,
        val nativeSegwitAddress: String
    ) : SetupState()

    data class Error(val message: String) : SetupState()
}
