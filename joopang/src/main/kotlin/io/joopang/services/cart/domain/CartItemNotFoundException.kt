package io.joopang.services.cart.domain

import java.util.UUID

class CartItemNotFoundException(cartItemId: UUID) : RuntimeException(
    "Cart item not found: $cartItemId",
)
