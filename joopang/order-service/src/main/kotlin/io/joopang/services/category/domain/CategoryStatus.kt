package io.joopang.services.category.domain

@JvmInline
value class CategoryStatus(val value: String) {

    init {
        require(value.isNotBlank()) { "Category status must not be blank" }
    }

    override fun toString(): String = value
}
