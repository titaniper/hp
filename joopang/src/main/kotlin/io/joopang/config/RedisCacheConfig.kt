package io.joopang.config

import io.joopang.common.cache.CacheNames
import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext

@Configuration(proxyBeanMethods = false)
@EnableCaching
@EnableConfigurationProperties(PopularProductsCacheProperties::class)
class RedisCacheConfig {

    @Bean
    fun redisCacheManager(
        connectionFactory: RedisConnectionFactory,
        popularProductsCacheProperties: PopularProductsCacheProperties,
    ): RedisCacheManager {
        val defaultCacheConfig = cacheConfig(Duration.ofMinutes(5))
        val popularProductsCacheConfig = cacheConfig(Duration.ofSeconds(popularProductsCacheProperties.ttlSeconds))

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultCacheConfig)
            .withInitialCacheConfigurations(
                mapOf(CacheNames.POPULAR_PRODUCTS to popularProductsCacheConfig),
            )
            .transactionAware()
            .build()
    }

    private fun cacheConfig(ttl: Duration): RedisCacheConfiguration =
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(ttl)
            .disableCachingNullValues()
            .prefixCacheNameWith("joopang:")
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(GenericJackson2JsonRedisSerializer()),
            )

}

@ConfigurationProperties(prefix = "joopang.cache.popular-products")
class PopularProductsCacheProperties {
    /** TTL in seconds for cached popular products to balance freshness vs DB load */
    var ttlSeconds: Long = 60
}
