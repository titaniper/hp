package io.joopang.config

import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.springframework.boot.autoconfigure.data.redis.RedisProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RedissonConfig(
    private val redisProperties: RedisProperties,
) {

    @Bean(destroyMethod = "shutdown")
    fun redissonClient(): RedissonClient {
        val config = Config()
        val serverConfig = config.useSingleServer()
        val address = "redis://${redisProperties.host}:${redisProperties.port}"
        serverConfig.address = address
        redisProperties.password?.takeIf { it.isNotBlank() }?.let { serverConfig.password = it }
        redisProperties.database?.let { serverConfig.database = it }

        return Redisson.create(config)
    }
}
