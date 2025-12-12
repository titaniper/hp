package io.joopang.services.order.infrastructure.messaging

import io.joopang.services.common.events.OrderPaidEvent
import io.joopang.services.order.application.OrderEventPublisher
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Profile("test")
@Component
class NoopOrderEventPublisher : OrderEventPublisher {
    override fun publishOrderPaid(event: OrderPaidEvent) {
        // ignored in tests
    }
}
