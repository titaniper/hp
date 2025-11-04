package io.joopang.domain.coupon

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class Coupon(
    val id: UUID,
    val userId: UUID,
    val couponTemplateId: UUID?,
    val type: CouponType,
    val value: BigDecimal,
    val issuedAt: Instant,
    val usedAt: Instant? = null,
    val expiredAt: Instant? = null,
    val orderId: UUID? = null,
) {

    init {
        require(value >= BigDecimal.ZERO) { "Coupon value cannot be negative" }
    }

    fun isUsed(): Boolean = usedAt != null

    fun isExpired(referenceTime: Instant = Instant.now()): Boolean =
        expiredAt?.let { referenceTime.isAfter(it) } ?: false
}
