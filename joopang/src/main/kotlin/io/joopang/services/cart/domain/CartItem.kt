package io.joopang.services.cart.domain

import io.joopang.services.common.domain.Quantity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
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
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "BIGINT")
    var id: Long = 0,

    @Column(name = "user_id", columnDefinition = "BIGINT", nullable = false)
    var userId: Long = 0,

    @Column(name = "product_id", columnDefinition = "BIGINT", nullable = false)
    var productId: Long = 0,

    @Column(name = "product_item_id", columnDefinition = "BIGINT", nullable = false)
    var productItemId: Long = 0,

    @Column(name = "quantity", nullable = false)
    var quantity: Quantity = Quantity(0),
) {

    fun copy(
        id: Long = this.id,
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
