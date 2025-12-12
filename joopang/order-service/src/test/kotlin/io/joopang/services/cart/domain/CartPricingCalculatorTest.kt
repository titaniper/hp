package io.joopang.services.cart.domain

import io.joopang.services.common.domain.Money
import io.joopang.services.common.domain.Quantity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CartPricingCalculatorTest {

    @Test
    fun `returns zero totals when empty`() {
        val totals = CartPricingCalculator.calculate(emptyList())

        assertThat(totals.subtotal).isEqualTo(Money.ZERO)
        assertThat(totals.total).isEqualTo(Money.ZERO)
    }

    @Test
    fun `calculates subtotal for available items`() {
        val lines = listOf(
            CartPricingLine(Money.of(1000L), Quantity(2)),
            CartPricingLine(Money.of(500L), Quantity(3)),
        )

        val totals = CartPricingCalculator.calculate(lines)

        assertThat(totals.subtotal.toBigDecimal()).isEqualByComparingTo("3500")
        assertThat(totals.total).isEqualTo(totals.subtotal)
    }
}
