package io.joopang.services.common.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class MoneyTest {

    @Test
    fun `plus adds amounts with scale`() {
        val lhs = Money.of(BigDecimal("10.50"))
        val rhs = Money.of(BigDecimal("2.25"))

        val result = lhs + rhs

        assertThat(result.toBigDecimal()).isEqualByComparingTo("12.75")
    }

    @Test
    fun `minus subtracts values`() {
        val lhs = Money.of(BigDecimal("20.00"))
        val rhs = Money.of(BigDecimal("5.50"))

        val result = lhs - rhs

        assertThat(result.toBigDecimal()).isEqualByComparingTo("14.50")
    }

    @Test
    fun `times with int multiplies amount`() {
        val money = Money.of(BigDecimal("3.30"))

        val result = money * 3

        assertThat(result.toBigDecimal()).isEqualByComparingTo("9.90")
    }

    @Test
    fun `times with BigDecimal preserves scale`() {
        val money = Money.of(BigDecimal("100"))

        val result = money * BigDecimal("0.075")

        assertThat(result.toBigDecimal()).isEqualByComparingTo("7.50")
    }
}
