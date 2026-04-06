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
            // Try the most common exchange API patterns
            val urls = listOf(
                "https://klingex.io/api/v1/ticker?symbol=NNSUSDT",
                "https://klingex.io/api/v1/market/ticker?pair=NNS-USDT",
                "https://klingex.io/api/v2/ticker/NNS-USDT"
            )

            var lastException: Exception = Exception("No endpoints responded")
            for (url in urls) {
                try {
                    val request = Request.Builder().url(url).build()
                    val response = okHttpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: continue
                        val json = JSONObject(body)
                        val price = json.optString("last", "0").toDoubleOrNull()
                            ?: json.optString("price", "0").toDoubleOrNull()
                            ?: json.optString("lastPrice", "0").toDoubleOrNull()
                            ?: 0.0
                        val change = json.optString("changePercent", "0").toDoubleOrNull()
                            ?: json.optString("priceChangePercent", "0").toDoubleOrNull()
                            ?: 0.0
                        val high = json.optString("high", "0").toDoubleOrNull() ?: 0.0
                        val low = json.optString("low", "0").toDoubleOrNull() ?: 0.0
                        val vol = json.optString("volume", "0").toDoubleOrNull() ?: 0.0

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
                } catch (e: Exception) {
                    lastException = e
                }
            }
            return Result.failure(lastException)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    /**
     * Fetches 24h OHLC candle data for price chart.
     * Returns list of (timestamp, close) pairs.
     */
    suspend fun getPriceHistory(): Result<List<Pair<Long, Double>>> = try {
        val url = "https://klingex.io/api/v1/klines?symbol=NNSUSDT&interval=1h&limit=24"
        val request = Request.Builder().url(url).build()
        val response = okHttpClient.newCall(request).execute()
        if (response.isSuccessful) {
            val body = response.body?.string() ?: "[]"
            val jsonArray = org.json.JSONArray(body)
            val result = mutableListOf<Pair<Long, Double>>()
            for (i in 0 until jsonArray.length()) {
                val candle = jsonArray.getJSONArray(i)
                val ts = candle.getLong(0)
                val close = candle.getString(4).toDoubleOrNull() ?: continue
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
