package io.joopang.application.order

import io.joopang.domain.common.Money
import java.time.Instant
import java.util.UUID

interface OrderDataTransmissionService {
    fun send(payload: OrderDataPayload)
    fun addToRetryQueue(payload: OrderDataPayload)
}

data class OrderDataPayload(
    val orderId: UUID,
    val userId: UUID,
    val items: List<OrderDataLineItem>,
    val totalAmount: Money,
    val discountAmount: Money,
    val paidAt: Instant?,
)

data class OrderDataLineItem(
    val productId: UUID?,
    val productItemId: UUID?,
    val quantity: Int,
    val unitPrice: Money,
    val subtotal: Money,
)
