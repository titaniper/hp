package io.joopang.services.cart.domain

class CartItemNotFoundException(cartItemId: Long) : RuntimeException(
    "Cart item not found: $cartItemId",
)
