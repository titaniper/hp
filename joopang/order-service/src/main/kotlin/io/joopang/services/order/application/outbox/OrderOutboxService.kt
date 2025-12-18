package io.joopang.services.order.application.outbox

import io.joopang.services.common.events.OrderPaidEvent

interface OrderOutboxService {
    fun enqueueOrderPaid(event: OrderPaidEvent)
}
