package io.joopang.services.coupon.application

import io.joopang.services.common.events.OrderPaidEvent
import io.joopang.services.coupon.contract.CouponStatus
import io.joopang.services.coupon.domain.Coupon
import io.joopang.services.coupon.infrastructure.CouponRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CouponUsageService(
    private val couponRepository: CouponRepository,
) {
    private val log = LoggerFactory.getLogger(CouponUsageService::class.java)

    @Transactional
    fun handleOrderPaid(event: OrderPaidEvent) {
        if (event.couponIds.isEmpty()) {
            return
        }
        event.couponIds.forEach { couponId ->
            processCoupon(event, couponId)
        }
    }

    private fun processCoupon(event: OrderPaidEvent, couponId: Long) {
        val coupon = couponRepository.findByIdForUpdate(couponId)
        if (coupon == null) {
            log.warn("결제 이벤트에 포함된 쿠폰을 찾을 수 없습니다. couponId={}, orderId={}", couponId, event.orderId)
            return
        }
        if (coupon.userId != event.userId) {
            log.warn(
                "쿠폰 사용자와 이벤트 사용자 불일치. couponId={}, couponUser={}, eventUser={}",
                couponId,
                coupon.userId,
                event.userId,
            )
            return
        }
        if (coupon.isUsed()) {
            if (coupon.orderId == event.orderId) {
                log.debug("이미 처리된 쿠폰 이벤트 무시. couponId={}, orderId={}", couponId, event.orderId)
            } else {
                log.warn(
                    "다른 주문에 사용된 쿠폰 이벤트 수신. couponId={}, existingOrderId={}, eventOrderId={}",
                    couponId,
                    coupon.orderId,
                    event.orderId,
                )
            }
            return
        }
        if (coupon.status != CouponStatus.AVAILABLE) {
            log.warn("사용 불가 상태의 쿠폰 이벤트 수신. couponId={}, status={}", couponId, coupon.status)
            return
        }
        val updated: Coupon = coupon.markUsed(orderId = event.orderId, usedAt = event.paidAt)
        couponRepository.save(updated)
    }
}
