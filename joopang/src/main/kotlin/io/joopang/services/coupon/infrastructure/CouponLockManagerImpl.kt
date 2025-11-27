package io.joopang.services.coupon.infrastructure

import io.joopang.services.coupon.application.CouponLockManager
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.concurrent.TimeUnit

/**
 * 쿠폰 템플릿 단위로 Redisson 기반 분산 락을 획득해 다중 인스턴스에서도 발급 순서를 보장한다.
 */
@Component
class CouponLockManagerImpl(
    private val redissonClient: RedissonClient,
) : CouponLockManager {

    override fun <T> withTemplateLock(templateId: Long, action: () -> T): T {
        val lock = redissonClient.getLock("$LOCK_KEY_PREFIX$templateId")
        val acquired = try {
            lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("쿠폰 발급 대기 중 문제가 발생했습니다. 다시 시도해주세요.", e)
        }
        if (!acquired) {
            throw IllegalStateException("쿠폰 발급 요청이 많습니다. 잠시 후 다시 시도해주세요.")
        }

        var unlocked = false
        return try {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(
                    object : TransactionSynchronization {
                        override fun afterCompletion(status: Int) {
                            if (lock.isHeldByCurrentThread) {
                                lock.unlock()
                            }
                        }
                    },
                )
                unlocked = true
            }
            action()
        } finally {
            if (!unlocked && lock.isHeldByCurrentThread) {
                lock.unlock()
            }
        }
    }

    companion object {
        private const val LOCK_KEY_PREFIX = "lock:coupon-template:"
        private const val LOCK_WAIT_SECONDS = 2L
        private const val LOCK_LEASE_SECONDS = 5L
    }
}
