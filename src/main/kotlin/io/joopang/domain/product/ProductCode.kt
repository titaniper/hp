package io.joopang.domain.product

@JvmInline
value class ProductCode(val value: String) {

    init {
        require(value.isNotBlank()) { "Product code must not be blank" }
    }

    override fun toString(): String = value
}
