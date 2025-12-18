package io.joopang.services.common.events

import java.math.BigDecimal
import java.time.Instant

data class OrderPaidEvent(
    val orderId: Long,
    val userId: Long,
    val totalAmount: BigDecimal,
    val discountAmount: BigDecimal,
    val paidAt: Instant,
    val items: List<OrderPaidLineItem>,
    val couponIds: List<Long> = emptyList(),
)

data class OrderPaidLineItem(
    val productId: Long?,
    val productItemId: Long?,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val subtotal: BigDecimal,
)
