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
        val today = LocalDate.now()
        val keys = (0 until days).map { i ->
            val date = today.minusDays(i).format(DateTimeFormatter.BASIC_ISO_DATE)
            "product:rank:daily:$date"
        }
        
        if (keys.isEmpty()) return emptyList()

        val tempKey = "temp:rank:${UUID.randomUUID()}"
        
        // ZUNIONSTORE
        // keys가 하나면 union 할 필요 없이 바로 조회해도 되지만, 통일성을 위해 union 사용 (또는 분기 처리)
        if (keys.size == 1) {
             return getTopFromKey(keys[0], limit)
        }

        redisTemplate.opsForZSet().unionAndStore(keys[0], keys.drop(1), tempKey)
        redisTemplate.expire(tempKey, Duration.ofSeconds(60))
        
        val result = getTopFromKey(tempKey, limit)
            
        redisTemplate.delete(tempKey)
        
        return result
    }

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
}
