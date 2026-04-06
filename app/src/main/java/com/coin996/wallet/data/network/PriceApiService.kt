package com.coin996.wallet.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// ─── Klingex API response models ──────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class KlingexTickerResponse(
    @Json(name = "symbol") val symbol: String = "",
    @Json(name = "last") val last: String = "0",        // Last traded price
    @Json(name = "bid") val bid: String = "0",
    @Json(name = "ask") val ask: String = "0",
    @Json(name = "high") val high: String = "0",        // 24h high
    @Json(name = "low") val low: String = "0",          // 24h low
    @Json(name = "volume") val volume: String = "0",    // 24h volume (base)
    @Json(name = "amount") val amount: String = "0",    // 24h amount (quote)
    @Json(name = "change") val change: String = "0",    // 24h price change
    @Json(name = "changePercent") val changePercent: String = "0"  // 24h % change
)

@JsonClass(generateAdapter = true)
data class KlingexKlineResponse(
    // Each candle: [timestamp, open, high, low, close, volume]
    @Json(name = "data") val data: List<List<String>> = emptyList()
)

@JsonClass(generateAdapter = true)
data class PriceData(
    val priceUsd: Double,
    val change24hPercent: Double,
    val high24h: Double,
    val low24h: Double,
    val volume24h: Double,
    val isPositive: Boolean = change24hPercent >= 0
)

// ─── Retrofit interface ────────────────────────────────────────────────────────

interface KlingexApiService {
    /**
     * Get ticker for NNS-USDT
     * Base URL: https://klingex.io/api/v1/
     *
     * Note: Klingex may not have a public REST API documented.
     * This uses a common exchange pattern — adjust endpoints once confirmed.
     */
    @GET("ticker/24hr")
    suspend fun getTicker(
        @Query("symbol") symbol: String = "NNSUSDT"
    ): KlingexTickerResponse

    @GET("klines")
    suspend fun getKlines(
        @Query("symbol") symbol: String = "NNSUSDT",
        @Query("interval") interval: String = "1h",
        @Query("limit") limit: Int = 24
    ): List<List<String>>
}

// ─── Fallback / alternative price source ──────────────────────────────────────

/**
 * Scrape price from Klingex trade page when REST API is unavailable.
 * This is a placeholder — implement with actual Klingex API docs.
 */
class PriceParser {
    fun parseFromHtml(html: String): Double? {
        // Simple regex to find price like "0.00123" near "NNS" on the page
        val regex = Regex("""NNS[^0-9]*([0-9]+\.[0-9]+)""")
        return regex.find(html)?.groupValues?.get(1)?.toDoubleOrNull()
    }
}
