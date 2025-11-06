package io.joopang.services.coupon.application

import java.util.UUID

interface CouponLockManager {
    fun <T> withTemplateLock(templateId: UUID, action: () -> T): T
}
