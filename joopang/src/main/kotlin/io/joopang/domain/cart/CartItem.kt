package io.joopang.domain.cart

import io.joopang.domain.common.Quantity
import java.util.UUID

data class CartItem(
    val id: UUID,
    val userId: UUID,
    val productId: UUID,
    val productItemId: UUID,
    val quantity: Quantity,
)
