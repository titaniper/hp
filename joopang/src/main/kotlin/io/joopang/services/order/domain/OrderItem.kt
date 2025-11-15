package io.joopang.services.order.domain

import io.joopang.services.common.domain.Money
import io.joopang.services.common.domain.Quantity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

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
class OrderItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "BIGINT")
    var id: Long = 0,

    @Column(name = "order_id", columnDefinition = "BIGINT", nullable = false)
    var orderId: Long? = null,

    @Column(name = "product_id", columnDefinition = "BIGINT")
    var productId: Long? = null,

    @Column(name = "product_item_id", columnDefinition = "BIGINT")
    var productItemId: Long? = null,

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

    fun copy(
        id: Long = this.id,
        orderId: Long? = this.orderId,
        productId: Long? = this.productId,
        productItemId: Long? = this.productItemId,
        productName: String = this.productName,
        quantity: Quantity = this.quantity,
        unitPrice: Money = this.unitPrice,
        subtotal: Money = this.subtotal,
        refundedAmount: Money = this.refundedAmount,
        refundedQuantity: Quantity = this.refundedQuantity,
    ): OrderItem =
        OrderItem(
            id = id,
            orderId = orderId,
            productId = productId,
            productItemId = productItemId,
            productName = productName,
            quantity = quantity,
            unitPrice = unitPrice,
            subtotal = subtotal,
            refundedAmount = refundedAmount,
            refundedQuantity = refundedQuantity,
        )
}
