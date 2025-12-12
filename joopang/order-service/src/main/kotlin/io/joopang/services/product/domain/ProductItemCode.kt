package io.joopang.services.product.domain

@JvmInline
value class ProductItemCode(val value: String) {

    init {
        require(value.isNotBlank()) { "Product item code must not be blank" }
    }

    override fun toString(): String = value
}
