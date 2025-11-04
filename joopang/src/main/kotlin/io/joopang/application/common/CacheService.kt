package io.joopang.application.common

interface CacheService {
    fun get(key: String): Any?
    fun put(key: String, value: Any, ttlSeconds: Long)
    fun evict(key: String)
}
