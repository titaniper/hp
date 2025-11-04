package io.joopang.domain.product

class InsufficientStockException(
    productId: String,
    itemId: String,
) : RuntimeException("Insufficient stock for product $productId item $itemId")
