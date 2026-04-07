package com.coin996.wallet.data.repository

import com.coin996.wallet.core.spv.TransactionItem
import com.coin996.wallet.core.spv.WalletManager
import com.coin996.wallet.data.network.KlingexApiService
import com.coin996.wallet.data.network.PriceData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WalletRepository @Inject constructor(
    val walletManager: WalletManager
) {
    val walletState = walletManager.state
    val transactions: StateFlow<List<TransactionItem>> = walletManager.transactions

    fun isWalletSetup(): Boolean = walletManager.isWalletExists()

    suspend fun createWallet() = walletManager.createNewWallet()

    suspend fun restoreWallet(words: List<String>, date: Long? = null) =
        walletManager.restoreFromMnemonic(words, date)

    suspend fun loadWallet() = walletManager.loadWallet()

    fun getReceiveAddress() = walletManager.getReceiveAddress()

    fun getMnemonicWords() = walletManager.getMnemonicWords()
}

@Singleton
class PriceRepository @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    /**
     * Fetches NNS-USDT price from Klingex.
     * Uses a direct HTTP request + JSON parsing since the exchange
     * may not have a fully documented public REST API.
     *
     * Update the URL to the actual Klingex ticker endpoint once confirmed.
     */
    suspend fun getPrice(): Result<PriceData> {
        try {
            // Using official KlingEx API Base URL found in documentation
            val url = "https://api.klingex.io/api/v1/market/ticker?market=NNS_USDT"
            
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return Result.failure(Exception("Empty body"))
                val json = JSONObject(body)
                
                // KlingEx API usually wraps data in a 'data' or 'result' object
                val data = json.optJSONObject("data") ?: json
                
                val price = data.optString("last", "0").toDoubleOrNull()
                    ?: data.optString("price", "0").toDoubleOrNull()
                    ?: 0.0
                val change = data.optString("change", "0").toDoubleOrNull()
                    ?: data.optString("priceChangePercent", "0").toDoubleOrNull()
                    ?: 0.0
                val high = data.optString("high", "0").toDoubleOrNull() ?: 0.0
                val low = data.optString("low", "0").toDoubleOrNull() ?: 0.0
                val vol = data.optString("volume", "0").toDoubleOrNull() ?: 0.0

                return Result.success(
                    PriceData(
                        priceUsd = price,
                        change24hPercent = change,
                        high24h = high,
                        low24h = low,
                        volume24h = vol
                    )
                )
            }
            return Result.failure(Exception("HTTP ${response.code}"))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    /**
     * Fetches 24h OHLC candle data for price chart.
     * Returns list of (timestamp, close) pairs.
     */
    suspend fun getPriceHistory(): Result<List<Pair<Long, Double>>> = try {
        // Updated historical endpoint based on standard KlingEx patterns
        val url = "https://api.klingex.io/api/v1/market/kline?market=NNS_USDT&interval=1h&limit=24"
        val request = Request.Builder().url(url).build()
        val response = okHttpClient.newCall(request).execute()
        if (response.isSuccessful) {
            val body = response.body?.string() ?: "{}"
            val json = JSONObject(body)
            val dataArray = json.optJSONArray("data") ?: org.json.JSONArray()
            val result = mutableListOf<Pair<Long, Double>>()
            for (i in 0 until dataArray.length()) {
                val candle = dataArray.getJSONArray(i)
                val ts = candle.getLong(0)
                val close = candle.optString(4, "0").toDoubleOrNull() ?: continue
                result.add(Pair(ts, close))
            }
            Result.success(result)
        } else {
            Result.failure(Exception("HTTP ${response.code}"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
