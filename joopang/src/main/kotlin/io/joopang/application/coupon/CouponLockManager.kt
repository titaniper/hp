package io.joopang.application.coupon

import java.util.UUID

fun interface CouponLockManager {
    fun <T> withTemplateLock(templateId: UUID, action: () -> T): T
}
