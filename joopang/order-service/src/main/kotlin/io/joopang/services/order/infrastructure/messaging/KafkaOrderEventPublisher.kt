package io.joopang.services.order.infrastructure.messaging

import io.joopang.services.common.events.OrderPaidEvent
import io.joopang.services.order.application.OrderEventPublisher
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Profile("!test")
@Component
class KafkaOrderEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, OrderPaidEvent>,
    @Value("\${kafka.topics.order-paid:order-paid}") private val orderPaidTopic: String,
) : OrderEventPublisher {

    private val logger = LoggerFactory.getLogger(KafkaOrderEventPublisher::class.java)

    override fun publishOrderPaid(event: OrderPaidEvent) {
        kafkaTemplate.send(orderPaidTopic, event.orderId.toString(), event)
            .whenComplete { _, ex ->
                if (ex != null) {
                    logger.error("Failed to publish order paid event for order {}", event.orderId, ex)
                } else {
                    logger.info("Published order paid event. orderId={} topic={}", event.orderId, orderPaidTopic)
                }
            }
    }
}
