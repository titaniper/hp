package io.joopang.services.order.domain

import io.joopang.services.common.domain.Money
import java.util.UUID

data class OrderDiscount(
    val id: UUID,
    val orderId: UUID,
    val type: OrderDiscountType,
    val referenceId: UUID?,
    val price: Money,
    val couponId: UUID? = null,
) {

    init {
        require(price >= Money.ZERO) { "Discount price cannot be negative" }
    }
}
