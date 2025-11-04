package io.joopang.domain.order

import io.joopang.domain.common.Money
import io.joopang.domain.common.Quantity
import java.util.UUID

data class OrderItem(
    val id: UUID,
    val orderId: UUID,
    val productId: UUID?,
    val productItemId: UUID?,
    val productName: String,
    val quantity: Quantity,
    val unitPrice: Money,
    val subtotal: Money,
    val refundedAmount: Money = Money.ZERO,
    val refundedQuantity: Quantity = Quantity(0),
) {

    init {
        require(productName.isNotBlank()) { "Product name must not be blank" }
        require(subtotal == expectedSubtotal()) {
            "Subtotal must equal unit price x quantity"
        }
        require(refundedAmount >= Money.ZERO) { "Refunded amount cannot be negative" }
        require(refundedQuantity.value <= quantity.value) {
            "Refunded quantity cannot exceed ordered quantity"
        }
    }

    fun expectedSubtotal(): Money = unitPrice * quantity.value

    fun refundableQuantity(): Quantity = Quantity(quantity.value - refundedQuantity.value)
}
