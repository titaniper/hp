package io.joopang.services.coupon.domain

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class Coupon(
    val id: UUID,
    val userId: UUID,
    val couponTemplateId: UUID?,
    val type: CouponType,
    val value: BigDecimal,
    val status: CouponStatus = CouponStatus.AVAILABLE,
    val issuedAt: Instant,
    val usedAt: Instant? = null,
    val expiredAt: Instant? = null,
    val orderId: UUID? = null,
) {

    init {
        require(value >= BigDecimal.ZERO) { "Coupon value cannot be negative" }
    }

    fun isUsed(): Boolean = status == CouponStatus.USED

    fun isExpired(referenceTime: Instant = Instant.now()): Boolean =
        status == CouponStatus.EXPIRED || expiredAt?.let { referenceTime.isAfter(it) } ?: false

    fun isAvailable(referenceTime: Instant = Instant.now()): Boolean =
        status == CouponStatus.AVAILABLE && !isExpired(referenceTime)

    fun markUsed(orderId: UUID, usedAt: Instant = Instant.now()): Coupon =
        copy(
            status = CouponStatus.USED,
            usedAt = usedAt,
            orderId = orderId,
        )

    fun expire(): Coupon =
        if (status == CouponStatus.EXPIRED) this else copy(status = CouponStatus.EXPIRED)
}
