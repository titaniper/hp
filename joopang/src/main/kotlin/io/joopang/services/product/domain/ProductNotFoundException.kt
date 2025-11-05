package io.joopang.services.product.domain

class ProductNotFoundException(productId: String) :
    RuntimeException("Product with id $productId was not found")
