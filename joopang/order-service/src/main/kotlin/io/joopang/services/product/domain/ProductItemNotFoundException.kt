package io.joopang.services.product.domain

class ProductItemNotFoundException(productId: String, itemId: String) :
    RuntimeException("Product item $itemId for product $productId was not found")
