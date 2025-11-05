package io.joopang.services.order.infrastructure

import io.joopang.services.order.application.OrderDataPayload
import io.joopang.services.order.application.OrderDataTransmissionService
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList

@Component
class OrderDataTransmissionServiceImpl : OrderDataTransmissionService {

    private val sentPayloads = CopyOnWriteArrayList<OrderDataPayload>()
    private val retryQueue = ConcurrentLinkedQueue<OrderDataPayload>()

    override fun send(payload: OrderDataPayload) {
        sentPayloads += payload
    }

    override fun addToRetryQueue(payload: OrderDataPayload) {
        retryQueue += payload
    }

    fun sentPayloads(): List<OrderDataPayload> = sentPayloads.toList()

    fun queuedRetries(): List<OrderDataPayload> = retryQueue.toList()
}
