package io.joopang.services.common.application

interface CacheService {
    fun get(key: String): Any?
    fun put(key: String, value: Any, ttlSeconds: Long)
    fun evict(key: String)
}
