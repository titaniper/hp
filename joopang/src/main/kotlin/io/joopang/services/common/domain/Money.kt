package io.joopang.services.common.domain

import java.math.BigDecimal
import java.math.RoundingMode

@JvmInline
value class Money private constructor(val amount: BigDecimal) : Comparable<Money> {

    init {
        require(amount.scale() <= SCALE) {
            "Money must not have more than $SCALE decimal places but was ${amount.scale()}"
        }
    }

    operator fun plus(other: Money): Money = of(amount.add(other.amount))

    operator fun minus(other: Money): Money = of(amount.subtract(other.amount))

    operator fun times(multiplier: Int): Money = of(amount.multiply(BigDecimal(multiplier)))

    operator fun times(multiplier: BigDecimal): Money = of(amount.multiply(multiplier))

    override fun compareTo(other: Money): Int = amount.compareTo(other.amount)

    fun isNegative(): Boolean = amount.signum() < 0

    fun isZero(): Boolean = amount.compareTo(BigDecimal.ZERO) == 0

    fun toBigDecimal(): BigDecimal = amount

    companion object {
        private const val SCALE = 2
        val ZERO: Money = Money(BigDecimal.ZERO.setScale(SCALE))

        fun of(value: BigDecimal): Money = Money(value.setScale(SCALE, RoundingMode.HALF_UP))

        fun of(value: Long): Money = of(BigDecimal.valueOf(value))

        fun of(value: Double): Money = of(BigDecimal.valueOf(value))
    }
}
