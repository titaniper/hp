package io.joopang.domain.coupon

import java.time.Instant
import java.util.UUID

interface CouponRepository {
    fun findById(couponId: UUID): Coupon?
    fun findUserCoupons(userId: UUID): List<Coupon>
    fun findUserCoupon(userId: UUID, couponId: UUID): Coupon?
    fun findUserCouponByTemplate(userId: UUID, couponTemplateId: UUID): Coupon?
    fun save(coupon: Coupon): Coupon
    fun markUsed(couponId: UUID, orderId: UUID, usedAt: Instant): Coupon
}
