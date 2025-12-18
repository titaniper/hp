package io.joopang.services.order.infrastructure.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import io.joopang.services.common.events.OrderPaidEvent
import io.joopang.services.order.application.outbox.OrderOutboxService
import io.joopang.services.order.domain.outbox.OrderOutboxAggregateType
import io.joopang.services.order.domain.outbox.OrderOutboxEvent
import io.joopang.services.order.domain.outbox.OrderOutboxEventType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OrderOutboxServiceImpl(
    private val objectMapper: ObjectMapper,
    private val orderOutboxEventRepository: OrderOutboxEventRepository,
) : OrderOutboxService {

    @Transactional
    override fun enqueueOrderPaid(event: OrderPaidEvent) {
        val payload = objectMapper.writeValueAsString(event)
        val entity = OrderOutboxEvent(
            aggregateType = OrderOutboxAggregateType.ORDER.value,
            aggregateId = event.orderId.toString(),
            eventType = OrderOutboxEventType.ORDER_PAID.value,
            payload = payload,
            occurredAt = event.paidAt,
        )
        orderOutboxEventRepository.save(entity)
    }
}
