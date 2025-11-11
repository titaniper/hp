package io.joopang.services.cart.domain

import io.joopang.services.common.domain.Quantity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "cart_items")
data class CartItem(
    @Id
    @Column(columnDefinition = "BINARY(16)")
    var id: UUID = UUID(0L, 0L),

    @Column(name = "user_id", columnDefinition = "BINARY(16)", nullable = false)
    var userId: UUID = UUID(0L, 0L),

    @Column(name = "product_id", columnDefinition = "BINARY(16)", nullable = false)
    var productId: UUID = UUID(0L, 0L),

    @Column(name = "product_item_id", columnDefinition = "BINARY(16)", nullable = false)
    var productItemId: UUID = UUID(0L, 0L),

    @Column(name = "quantity", nullable = false)
    var quantity: Quantity = Quantity(0),
)
