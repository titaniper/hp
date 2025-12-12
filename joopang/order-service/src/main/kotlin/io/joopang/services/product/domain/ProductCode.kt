package io.joopang.services.product.domain

@JvmInline
value class ProductCode(val value: String) {

    init {
        require(value.isNotBlank()) { "Product code must not be blank" }
    }

    override fun toString(): String = value
}
