package com.coin996.wallet.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ─── Entity ───────────────────────────────────────────────────────────────────

@Entity(tableName = "cached_transactions")
data class CachedTransaction(
    @PrimaryKey val txHash: String,
    val amountSatoshi: Long,
    val isSent: Boolean,
    val isConfirmed: Boolean,
    val confirmations: Int,
    val timestamp: Long,
    val toAddress: String?,
    val cachedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "price_cache")
data class PriceCache(
    @PrimaryKey val id: Int = 1,   // single row
    val priceUsd: Double,
    val change24hPercent: Double,
    val high24h: Double,
    val low24h: Double,
    val volume24h: Double,
    val updatedAt: Long = System.currentTimeMillis()
)

// ─── DAOs ─────────────────────────────────────────────────────────────────────

@Dao
interface TransactionDao {
    @Query("SELECT * FROM cached_transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<CachedTransaction>>

    @Query("SELECT * FROM cached_transactions ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentTransactions(limit: Int = 10): Flow<List<CachedTransaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<CachedTransaction>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: CachedTransaction)

    @Query("DELETE FROM cached_transactions")
    suspend fun deleteAll()
}

@Dao
interface PriceCacheDao {
    @Query("SELECT * FROM price_cache WHERE id = 1")
    suspend fun getPrice(): PriceCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(price: PriceCache)
}

// ─── Database ─────────────────────────────────────────────────────────────────

@Database(
    entities = [CachedTransaction::class, PriceCache::class],
    version = 1,
    exportSchema = false
)
abstract class WalletDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun priceCacheDao(): PriceCacheDao

    companion object {
        const val DATABASE_NAME = "996coin_wallet.db"
    }
}
