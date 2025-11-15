package io.joopang.services.coupon.application

interface CouponLockManager {
    fun <T> withTemplateLock(templateId: Long, action: () -> T): T
}
