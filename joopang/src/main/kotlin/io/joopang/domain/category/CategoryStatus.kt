package io.joopang.domain.category

@JvmInline
value class CategoryStatus(val value: String) {

    init {
        require(value.isNotBlank()) { "Category status must not be blank" }
    }

    override fun toString(): String = value
}
