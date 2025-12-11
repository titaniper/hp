package io.joopang.services.order.infrastructure

import io.joopang.services.order.application.OrderDataPayload
import io.joopang.services.order.application.OrderDataTransmissionService
import io.joopang.services.product.infrastructure.ProductRankingRepository
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList

@Component
class OrderDataTransmissionServiceImpl(
    private val productRankingRepository: ProductRankingRepository,
) : OrderDataTransmissionService {

    private val sentPayloads = CopyOnWriteArrayList<OrderDataPayload>()
    private val retryQueue = ConcurrentLinkedQueue<OrderDataPayload>()

    override fun send(payload: OrderDataPayload) {
        sentPayloads += payload

        payload.items.forEach { item ->
            item.productId?.let { productId ->
                productRankingRepository.incrementSalesAndRevenue(
                    productId = productId,
                    quantity = item.quantity.toDouble(),
                    revenue = item.subtotal.toBigDecimal().toDouble(),
                )
            }
        }
    }

    override fun addToRetryQueue(payload: OrderDataPayload) {
        retryQueue += payload
    }

    fun sentPayloads(): List<OrderDataPayload> = sentPayloads.toList()

    fun queuedRetries(): List<OrderDataPayload> = retryQueue.toList()
}
