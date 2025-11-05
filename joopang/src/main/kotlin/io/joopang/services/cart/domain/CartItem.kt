package io.joopang.services.cart.domain

import io.joopang.services.common.domain.Quantity
import java.util.UUID

data class CartItem(
    val id: UUID,
    val userId: UUID,
    val productId: UUID,
    val productItemId: UUID,
    val quantity: Quantity,
)
