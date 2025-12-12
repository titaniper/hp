package io.joopang.services.order.application.coupon

import io.joopang.services.common.domain.Money
import io.joopang.services.coupon.contract.CouponSnapshot
import io.joopang.services.coupon.contract.CouponStatus
import io.joopang.services.coupon.contract.CouponType
import io.joopang.services.coupon.contract.InvalidCouponException
import java.time.Instant

interface CouponClient {
    fun getCouponForOrder(couponId: Long, userId: Long): CouponSnapshot
    fun markCouponUsed(couponId: Long, userId: Long, orderId: Long)
}

fun CouponSnapshot.assertUsable(referenceTime: Instant = Instant.now()) {
    if (status != CouponStatus.AVAILABLE) {
        throw InvalidCouponException("Coupon $id is not available for use")
    }
    if (isExpired(referenceTime)) {
        throw InvalidCouponException("Coupon $id is expired")
    }
}

fun CouponSnapshot.isExpired(referenceTime: Instant = Instant.now()): Boolean =
    expiredAt?.let { referenceTime.isAfter(it) } ?: false

fun CouponSnapshot.calculateDiscount(subtotal: Money): Money {
    val rawDiscount = when (type) {
        CouponType.PERCENTAGE -> subtotal * value
        CouponType.AMOUNT -> Money.of(value)
        CouponType.GIFT -> subtotal
    }
    return if (rawDiscount > subtotal) subtotal else rawDiscount
}
