package io.joopang.services.coupon.infrastructure

import io.joopang.services.coupon.application.CouponLockManager
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Component
class CouponLockManagerImpl : CouponLockManager {

    private val locks = ConcurrentHashMap<Long, ReentrantLock>()

    override fun <T> withTemplateLock(templateId: Long, action: () -> T): T {
        val lock = locks.computeIfAbsent(templateId) { ReentrantLock() }
        return lock.withLock { action() }
    }
}
