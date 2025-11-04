package io.joopang.domain.product

import java.math.BigDecimal
import java.math.RoundingMode

@JvmInline
value class StockQuantity private constructor(val value: BigDecimal) {

    init {
        require(value >= BigDecimal.ZERO) { "Stock quantity must be non-negative" }
    }

    operator fun plus(delta: StockQuantity): StockQuantity = of(value.add(delta.value))

    operator fun minus(delta: StockQuantity): StockQuantity {
        val result = value.subtract(delta.value)
        require(result >= BigDecimal.ZERO) { "Stock quantity cannot go below zero" }
        return of(result)
    }

    companion object {
        fun of(value: BigDecimal): StockQuantity =
            StockQuantity(value.setScale(2, RoundingMode.HALF_UP))
    }
}
