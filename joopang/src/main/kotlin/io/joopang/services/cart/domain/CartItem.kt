package io.joopang.services.cart.domain

import io.joopang.services.common.domain.BaseEntity
import io.joopang.services.common.domain.Quantity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(
    name = "cart_items",
    indexes = [
        Index(
            name = "idx_cart_items_user_product_item",
            columnList = "user_id, product_item_id",
            unique = true,
        ),
    ],
)
class CartItem(
    id: Long? = null,
    @Column(name = "user_id", columnDefinition = "BIGINT", nullable = false)
    var userId: Long = 0,

    @Column(name = "product_id", columnDefinition = "BIGINT", nullable = false)
    var productId: Long = 0,

    @Column(name = "product_item_id", columnDefinition = "BIGINT", nullable = false)
    var productItemId: Long = 0,

    @Column(name = "quantity", nullable = false)
    var quantity: Quantity = Quantity(0),
) : BaseEntity(id) {

    @Suppress("unused")
    constructor() : this(
        id = null,
        userId = 0,
        productId = 0,
        productItemId = 0,
        quantity = Quantity(0),
    )

    fun copy(
        id: Long? = this.id,
        userId: Long = this.userId,
        productId: Long = this.productId,
        productItemId: Long = this.productItemId,
        quantity: Quantity = this.quantity,
    ): CartItem =
        CartItem(
            id = id,
            userId = userId,
            productId = productId,
            productItemId = productItemId,
            quantity = quantity,
        )
}
