package io.joopang.services.coupon.infrastructure

import io.joopang.services.coupon.domain.Coupon
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface CouponRepository : JpaRepository<Coupon, Long> {
    fun findAllByUserId(userId: Long): List<Coupon>
    fun findByIdAndUserId(id: Long, userId: Long): Coupon?
    fun findByUserIdAndCouponTemplateId(userId: Long, couponTemplateId: Long): Coupon?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Coupon c where c.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): Coupon?
}
