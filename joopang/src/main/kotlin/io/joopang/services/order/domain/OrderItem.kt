package io.joopang.services.order.domain

import io.joopang.services.common.domain.Money
import io.joopang.services.common.domain.Quantity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(
    name = "order_items",
    indexes = [
        Index(
            name = "idx_order_items_order_id",
            columnList = "order_id",
        ),
        Index(
            name = "idx_order_items_product_id",
            columnList = "product_id",
        ),
    ],
)
data class OrderItem(
    @Id
    @Column(columnDefinition = "BINARY(16)")
    var id: UUID = UUID(0L, 0L),

    @Column(name = "order_id", columnDefinition = "BINARY(16)", nullable = false)
    var orderId: UUID = UUID(0L, 0L),

    @Column(name = "product_id", columnDefinition = "BINARY(16)")
    var productId: UUID? = null,

    @Column(name = "product_item_id", columnDefinition = "BINARY(16)")
    var productItemId: UUID? = null,

    @Column(name = "product_name", nullable = false)
    var productName: String = "",

    @Column(nullable = false)
    var quantity: Quantity = Quantity(0),

    @Column(name = "unit_price", precision = 19, scale = 2, nullable = false)
    var unitPrice: Money = Money.ZERO,

    @Column(name = "subtotal", precision = 19, scale = 2, nullable = false)
    var subtotal: Money = Money.ZERO,

    @Column(name = "refunded_amount", precision = 19, scale = 2, nullable = false)
    var refundedAmount: Money = Money.ZERO,

    @Column(name = "refunded_quantity", nullable = false)
    var refundedQuantity: Quantity = Quantity(0),
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
