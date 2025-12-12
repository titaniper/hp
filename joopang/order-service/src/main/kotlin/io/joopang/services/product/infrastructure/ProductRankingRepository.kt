package io.joopang.services.product.infrastructure

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

@Repository
class ProductRankingRepository(
    private val redisTemplate: StringRedisTemplate,
) {
    fun incrementSalesAndRevenue(productId: Long, quantity: Double, revenue: Double) {
        val today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
        
        redisTemplate.executePipelined { connection ->
            val productIdBytes = productId.toString().toByteArray()
            
            // Sales (1h)
            val sales1hKey = "product:rank:sales:1h".toByteArray()
            connection.zIncrBy(sales1hKey, quantity, productIdBytes)
            connection.expire(sales1hKey, Duration.ofMinutes(120).seconds)
            
            // Sales (Daily)
            val salesDailyKey = "product:rank:daily:$today".toByteArray()
            connection.zIncrBy(salesDailyKey, quantity, productIdBytes)
            connection.expire(salesDailyKey, Duration.ofDays(5).seconds)
            
            // Revenue (Daily)
            val revenueDailyKey = "product:rank:revenue:daily:$today".toByteArray()
            connection.zIncrBy(revenueDailyKey, revenue, productIdBytes)
            connection.expire(revenueDailyKey, Duration.ofDays(5).seconds)
            
            null
        }
    }

    fun getTopProductIdsWithScores(days: Long, limit: Int): List<Pair<Long, Double>> {
        require(days > 0) { "Days must be greater than zero" }

        val cacheKey = popularAggregateKey(days)
        if (redisTemplate.hasKey(cacheKey) != true) {
            refreshPopularProductsCache(days)
        }
        return getTopFromKey(cacheKey, limit)
    }

    fun refreshPopularProductsCache(days: Long) {
        require(days > 0) { "Days must be greater than zero" }
        val keys = dailyKeys(days)
        if (keys.isEmpty()) return

        val tempKey = "${popularAggregateKey(days)}:tmp:${UUID.randomUUID()}"
        redisTemplate.opsForZSet().unionAndStore(keys.first(), keys.drop(1), tempKey)

        redisTemplate.execute { connection ->
            val tempKeyBytes = tempKey.toByteArray()
            if (!connection.exists(tempKeyBytes)) {
                return@execute null
            }
            val destinationKeyBytes = popularAggregateKey(days).toByteArray()
            connection.rename(tempKeyBytes, destinationKeyBytes)
            connection.expire(destinationKeyBytes, POPULAR_CACHE_TTL.seconds)
            null
        }
    }

    private fun dailyKeys(days: Long): List<String> {
        val today = LocalDate.now()
        return (0 until days).map { i ->
            val date = today.minusDays(i).format(DateTimeFormatter.BASIC_ISO_DATE)
            "product:rank:daily:$date"
        }
    }

    private fun popularAggregateKey(days: Long): String =
        "product:rank:popular:$days"

    private fun getTopFromKey(key: String, limit: Int): List<Pair<Long, Double>> {
        return redisTemplate.opsForZSet()
            .reverseRangeWithScores(key, 0, (limit - 1).toLong())
            ?.mapNotNull { tuple ->
                tuple.value?.toLongOrNull()?.let { id ->
                    id to (tuple.score ?: 0.0)
                }
            }
            ?: emptyList()
    }

    companion object {
        private val POPULAR_CACHE_TTL: Duration = Duration.ofMinutes(10)
    }
}
