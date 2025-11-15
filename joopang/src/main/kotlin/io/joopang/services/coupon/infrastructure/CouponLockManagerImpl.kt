package io.joopang.services.coupon.infrastructure

import io.joopang.services.coupon.application.CouponLockManager
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

@Component
class CouponLockManagerImpl : CouponLockManager {

    private val locks = ConcurrentHashMap<Long, ReentrantLock>()

    override fun <T> withTemplateLock(templateId: Long, action: () -> T): T {
        val lock = locks.computeIfAbsent(templateId) { ReentrantLock() }
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
