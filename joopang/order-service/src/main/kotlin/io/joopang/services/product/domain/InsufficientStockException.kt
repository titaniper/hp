package io.joopang.services.product.domain

class InsufficientStockException(
    productId: String,
    itemId: String,
) : RuntimeException("Insufficient stock for product $productId item $itemId")
