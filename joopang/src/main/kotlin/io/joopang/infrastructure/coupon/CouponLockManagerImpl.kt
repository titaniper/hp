package io.joopang.infrastructure.coupon

import io.joopang.application.coupon.CouponLockManager
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Component
class CouponLockManagerImpl : CouponLockManager {

    private val locks = ConcurrentHashMap<UUID, ReentrantLock>()

    override fun <T> withTemplateLock(templateId: UUID, action: () -> T): T {
        val lock = locks.computeIfAbsent(templateId) { ReentrantLock() }
        return lock.withLock { action() }
    }
}
