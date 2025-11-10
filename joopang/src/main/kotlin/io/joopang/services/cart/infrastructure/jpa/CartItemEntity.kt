package io.joopang.services.cart.infrastructure.jpa

import io.joopang.services.cart.domain.CartItem
import io.joopang.services.common.domain.Quantity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "cart_items")
class CartItemEntity(
    @Id
    @Column(columnDefinition = "BINARY(16)")
    var id: UUID,

    @Column(name = "user_id", columnDefinition = "BINARY(16)", nullable = false)
    var userId: UUID,

    @Column(name = "product_id", columnDefinition = "BINARY(16)", nullable = false)
    var productId: UUID,

    @Column(name = "product_item_id", columnDefinition = "BINARY(16)", nullable = false)
    var productItemId: UUID,

    @Column(name = "quantity", nullable = false)
    var quantity: Quantity,
) {
    fun toDomain(): CartItem = CartItem(
        id = id,
        userId = userId,
        productId = productId,
        productItemId = productItemId,
        quantity = quantity,
    )

    companion object {
        fun from(domain: CartItem): CartItemEntity = CartItemEntity(
            id = domain.id,
            userId = domain.userId,
            productId = domain.productId,
            productItemId = domain.productItemId,
            quantity = domain.quantity,
        )
    }
}
