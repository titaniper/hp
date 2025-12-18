package io.joopang.services.coupon.application

import io.joopang.services.coupon.contract.CouponNotFoundException
import io.joopang.services.coupon.contract.CouponStatus
import io.joopang.services.coupon.contract.InvalidCouponException
import io.joopang.services.coupon.domain.Coupon
import io.joopang.services.coupon.infrastructure.CouponRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class CouponOrderFacade(
    private val couponRepository: CouponRepository,
) {

    @Transactional(readOnly = true)
    fun getCouponForOrder(couponId: Long, userId: Long): Coupon {
        val coupon = couponRepository.findByIdAndUserId(couponId, userId)
            ?: throw CouponNotFoundException(couponId.toString())
        validateAvailability(coupon)
        return coupon
    }

    private fun validateAvailability(coupon: Coupon) {
        if (coupon.status != CouponStatus.AVAILABLE) {
            throw InvalidCouponException("Coupon ${coupon.id} is not available")
        }
        if (coupon.isExpired()) {
            throw InvalidCouponException("Coupon ${coupon.id} is expired")
        }
    }
}
