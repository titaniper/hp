package io.joopang.domain.order

import io.joopang.domain.common.Money
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
