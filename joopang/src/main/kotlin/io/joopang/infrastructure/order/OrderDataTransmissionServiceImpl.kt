package io.joopang.infrastructure.order

import io.joopang.application.order.OrderDataPayload
import io.joopang.application.order.OrderDataTransmissionService
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
