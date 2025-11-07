package io.joopang.services.coupon.infrastructure

import io.joopang.services.coupon.domain.Coupon
import io.joopang.services.coupon.domain.CouponStatus
import io.joopang.services.coupon.domain.CouponType
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Repository
open class CouponRepository {

    private val store = ConcurrentHashMap<UUID, Coupon>()

    init {
        seed()
    }

    open fun findById(couponId: UUID): Coupon? = store[couponId]

    open fun findUserCoupons(userId: UUID): List<Coupon> =
        store.values.filter { it.userId == userId }

    open fun findUserCoupon(userId: UUID, couponId: UUID): Coupon? =
        store[couponId]?.takeIf { it.userId == userId }

    open fun findUserCouponByTemplate(userId: UUID, couponTemplateId: UUID): Coupon? =
        store.values.firstOrNull { it.userId == userId && it.couponTemplateId == couponTemplateId }

    open fun save(coupon: Coupon): Coupon {
        store[coupon.id] = coupon
        return coupon
    }

    private fun seed() {
        val userId = UUID.fromString("aaaaaaaa-1111-2222-3333-444444444444")
        val percentageCouponId = UUID.fromString("10101010-2020-3030-4040-505050505050")
        store[percentageCouponId] = Coupon(
            id = percentageCouponId,
            userId = userId,
            couponTemplateId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
            type = CouponType.PERCENTAGE,
            value = BigDecimal("0.10"),
            status = CouponStatus.AVAILABLE,
            issuedAt = Instant.now().minus(3, ChronoUnit.DAYS),
            expiredAt = Instant.now().plus(30, ChronoUnit.DAYS),
        )

        val amountCouponId = UUID.fromString("60606060-7070-8080-9090-a0a0a0a0a0a0")
        store[amountCouponId] = Coupon(
            id = amountCouponId,
            userId = userId,
            couponTemplateId = UUID.fromString("bbbbbbbb-cccc-dddd-eeee-ffffffffffff"),
            type = CouponType.AMOUNT,
            value = BigDecimal("5000"),
            status = CouponStatus.AVAILABLE,
            issuedAt = Instant.now().minus(1, ChronoUnit.DAYS),
            expiredAt = Instant.now().plus(15, ChronoUnit.DAYS),
        )
    }
}
