package io.joopang.services.product.domain

class ProductItemInactiveException(
    productId: String,
    productItemId: String,
) : RuntimeException("Product item $productItemId for product $productId is not active")
