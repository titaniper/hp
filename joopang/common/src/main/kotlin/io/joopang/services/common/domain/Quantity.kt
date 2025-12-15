package io.joopang.services.common.domain

@JvmInline
value class Quantity(val value: Int) {

    init {
        require(value >= 0) { "Quantity must be non-negative but was $value" }
    }

    operator fun plus(other: Quantity): Quantity = Quantity(value + other.value)

    operator fun minus(other: Quantity): Quantity {
        val result = value - other.value
        require(result >= 0) { "Quantity cannot be negative after subtraction" }
        return Quantity(result)
    }

    override fun toString(): String = value.toString()
}
