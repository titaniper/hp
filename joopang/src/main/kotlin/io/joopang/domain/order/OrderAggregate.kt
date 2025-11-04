package io.joopang.domain.order

import io.joopang.domain.common.Money
import java.util.UUID

data class OrderAggregate(
    val order: Order,
    val items: List<OrderItem>,
    val discounts: List<OrderDiscount>,
) {

    init {
        require(items.isNotEmpty()) { "Order must have at least one item" }
        require(items.all { it.orderId == order.id }) { "Order item must reference the same order" }
        require(discounts.all { it.orderId == order.id }) { "Order discount must reference the same order" }
    }

    fun totalItemSubtotal(): Money =
        items.fold(Money.ZERO) { acc, item -> acc + item.subtotal }

    fun totalDiscount(): Money =
        discounts.fold(Money.ZERO) { acc, discount -> acc + discount.price }

    fun payableAmount(): Money = order.payableAmount()
}
