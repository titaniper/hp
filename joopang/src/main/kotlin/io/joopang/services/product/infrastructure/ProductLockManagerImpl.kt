package io.joopang.services.product.infrastructure

import io.joopang.services.order.application.ProductLockManager
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

@Component
class ProductLockManagerImpl : ProductLockManager {

    private val locks = ConcurrentHashMap<Long, ReentrantLock>()

    override fun <T> withProductLock(productId: Long, action: () -> T): T {
        val lock = locks.computeIfAbsent(productId) { ReentrantLock() }
        lock.lock()
        var unlocked = false
        return try {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(
                    object : TransactionSynchronization {
                        override fun afterCompletion(status: Int) {
                            lock.unlock()
                        }
                    },
                )
                unlocked = true
            }
            action()
        } finally {
            if (!unlocked) {
                lock.unlock()
            }
        }
    }
}
