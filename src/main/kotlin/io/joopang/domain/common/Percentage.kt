package io.joopang.domain.common

import java.math.BigDecimal
import java.math.RoundingMode

@JvmInline
value class Percentage private constructor(val value: BigDecimal) {

    init {
        require(value >= MIN && value <= MAX) {
            "Percentage must be between $MIN and $MAX but was $value"
        }
    }

    fun toDecimalFraction(scale: Int = 4): BigDecimal =
        value.divide(BigDecimal(100), scale, RoundingMode.HALF_UP)

    companion object {
        private val MIN = BigDecimal.ZERO
        private val MAX = BigDecimal(100)

        fun of(value: BigDecimal): Percentage = Percentage(value.setScale(2, RoundingMode.HALF_UP))

        fun of(value: Double): Percentage = of(BigDecimal.valueOf(value))
    }
}
