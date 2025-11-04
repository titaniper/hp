package io.joopang.domain.product

class ProductNotFoundException(productId: String) :
    RuntimeException("Product with id $productId was not found")
