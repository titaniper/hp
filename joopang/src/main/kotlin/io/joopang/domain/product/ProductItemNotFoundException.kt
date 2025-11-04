package io.joopang.domain.product

class ProductItemNotFoundException(productId: String, itemId: String) :
    RuntimeException("Product item $itemId for product $productId was not found")
