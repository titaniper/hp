package io.joopang.services.order.application

import io.joopang.services.common.events.OrderPaidEvent

interface OrderEventPublisher {
    fun publishOrderPaid(event: OrderPaidEvent)
}
