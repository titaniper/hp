package io.joopang.services.common.infrastructure

import io.joopang.services.common.application.CacheService
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Component
class CacheServiceImpl : CacheService {

    private data class CacheEntry(
        val value: Any,
        val expiresAt: Instant?,
    )

    private val store = ConcurrentHashMap<String, CacheEntry>()

    override fun get(key: String): Any? {
        val entry = store[key] ?: return null
        if (entry.expiresAt != null && Instant.now().isAfter(entry.expiresAt)) {
            store.remove(key)
            return null
        }
        return entry.value
    }

    override fun put(key: String, value: Any, ttlSeconds: Long) {
        val expiresAt = if (ttlSeconds > 0) Instant.now().plusSeconds(ttlSeconds) else null
        store[key] = CacheEntry(value, expiresAt)
    }

    override fun evict(key: String) {
        store.remove(key)
    }
}
