package io.joopang.services.order.domain

import io.joopang.services.common.domain.BaseEntity
import io.joopang.services.common.domain.Money
import io.joopang.services.common.domain.Quantity
import jakarta.persistence.Column
import jakarta.persistence.ConstraintMode
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
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
    id: Long? = null,
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "order_id",
        nullable = false,
        foreignKey = ForeignKey(ConstraintMode.NO_CONSTRAINT),
    )
    var order: Order? = null,
) : BaseEntity(id) {

    init {
        if (id != null || productName.isNotBlank()) {
            require(productName.isNotBlank()) { "Product name must not be blank" }
            require(subtotal == expectedSubtotal()) {
                "Subtotal must equal unit price x quantity"
            }
            require(refundedAmount >= Money.ZERO) { "Refunded amount cannot be negative" }
            require(refundedQuantity.value <= quantity.value) {
                "Refunded quantity cannot exceed ordered quantity"
            }
        }
    }

    fun expectedSubtotal(): Money = unitPrice * quantity.value

    fun refundableQuantity(): Quantity = Quantity(quantity.value - refundedQuantity.value)

    @Suppress("unused")
    constructor() : this(
        id = null,
        productId = null,
        productItemId = null,
        productName = "",
        quantity = Quantity(0),
        unitPrice = Money.ZERO,
        subtotal = Money.ZERO,
        refundedAmount = Money.ZERO,
        refundedQuantity = Quantity(0),
    )

    fun copy(
        id: Long? = this.id,
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
            productId = productId,
            productItemId = productItemId,
            productName = productName,
            quantity = quantity,
            unitPrice = unitPrice,
            subtotal = subtotal,
            refundedAmount = refundedAmount,
            refundedQuantity = refundedQuantity,
        ).also {
            it.order = order
        }
}
