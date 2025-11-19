package io.joopang.services.coupon.infrastructure

import io.joopang.services.coupon.domain.Coupon
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CouponRepository : JpaRepository<Coupon, Long> {
    fun findAllByUserId(userId: Long): List<Coupon>
    fun findByIdAndUserId(id: Long, userId: Long): Coupon?
    fun findByUserIdAndCouponTemplateId(userId: Long, couponTemplateId: Long): Coupon?
}
