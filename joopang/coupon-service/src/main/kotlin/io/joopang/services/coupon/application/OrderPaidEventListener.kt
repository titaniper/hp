package io.joopang.services.coupon.application

import io.joopang.services.common.events.OrderPaidEvent
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class OrderPaidEventListener(
    private val couponUsageService: CouponUsageService,
) {

    @KafkaListener(
        topics = ["\${kafka.topics.order-paid:order-paid}"],
        groupId = "\${kafka.order-paid.group-id:coupon-order-paid-worker}",
    )
    fun handle(event: OrderPaidEvent) {
        couponUsageService.handleOrderPaid(event)
    }
}
