package io.joopang.services.product.infrastructure

import io.joopang.services.order.application.ProductLockManager
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Component
class ProductLockManagerImpl : ProductLockManager {

    private val locks = ConcurrentHashMap<UUID, ReentrantLock>()

    override fun <T> withProductLock(productId: UUID, action: () -> T): T {
        val lock = locks.computeIfAbsent(productId) { ReentrantLock() }
        return lock.withLock { action() }
    }
}
