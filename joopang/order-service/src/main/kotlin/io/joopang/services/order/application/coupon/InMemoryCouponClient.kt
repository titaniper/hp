package io.joopang.services.order.application.coupon

import io.joopang.services.coupon.contract.CouponNotFoundException
import io.joopang.services.coupon.contract.CouponSnapshot
import io.joopang.services.coupon.contract.CouponStatus
import io.joopang.services.coupon.contract.InvalidCouponException
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Profile("test")
@Component
class InMemoryCouponClient : CouponClient {

    private val coupons = ConcurrentHashMap<Long, CouponSnapshot>()

    override fun getCouponForOrder(couponId: Long, userId: Long): CouponSnapshot {
        val coupon = coupons[couponId] ?: throw CouponNotFoundException(couponId.toString())
        if (coupon.userId != userId) {
            throw InvalidCouponException("Coupon $couponId does not belong to user $userId")
        }
        coupon.assertUsable(Instant.now())
        return coupon
    }

    fun registerCoupon(snapshot: CouponSnapshot) {
        coupons[snapshot.id] = snapshot
    }

    fun reset() {
        coupons.clear()
    }
}
